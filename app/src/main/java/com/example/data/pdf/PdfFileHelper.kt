package com.example.data.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object PdfFileHelper {

    private const val SAVED_PDFS_DIR = "saved_pdfs"

    /**
     * Resolves the user-facing display name of a PDF given its Uri.
     */
    fun getDisplayName(context: Context, uri: Uri): String {
        var fileName = "Document.pdf"
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            cursor.getString(nameIndex)?.let { fileName = it }
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                uri.path?.let { path ->
                    val f = File(path)
                    if (f.name.isNotBlank()) fileName = f.name
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (!fileName.endsWith(".pdf", ignoreCase = true)) {
            fileName += ".pdf"
        }
        return fileName
    }

    /**
     * Proactively copies any external PDF into internal app storage so that
     * future reads NEVER fail due to expired SAF permissions, device restarts,
     * or deleted source downloads.
     */
    fun copyToInternalStorage(context: Context, sourceUri: Uri, originalFileName: String): Uri {
        // If it's already an internal file in the app's files directory, return as-is
        if (sourceUri.scheme == "file") {
            val file = File(sourceUri.path ?: "")
            if (file.exists() && file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                return sourceUri
            }
        }

        // Try persistable URI permission first if content
        if (sourceUri.scheme == "content") {
            try {
                context.contentResolver.takePersistableUriPermission(
                    sourceUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {
                // Not all content providers support persistable permissions
            }
        }

        val dir = File(context.filesDir, SAVED_PDFS_DIR).apply { mkdirs() }
        val cleanName = originalFileName
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            .let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }

        // Generate deterministic filename from source URI so multiple imports of same URI reuse file
        val uriHash = (sourceUri.toString().hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        val targetFile = File(dir, "${uriHash}_$cleanName")

        if (targetFile.exists() && targetFile.length() > 0) {
            return Uri.fromFile(targetFile)
        }

        try {
            val inputStream = openInputStream(context, sourceUri)
            if (inputStream != null) {
                inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    return Uri.fromFile(targetFile)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to original URI if copy fails
        return sourceUri
    }

    /**
     * Safely opens a ParcelFileDescriptor for both file:// and content:// URIs.
     * Uses ParcelFileDescriptor.open directly for file:// URIs, avoiding ContentResolver errors.
     */
    fun openParcelFileDescriptor(context: Context, uri: Uri, fallbackTitle: String? = null): ParcelFileDescriptor? {
        if (uri.scheme == "file") {
            try {
                val file = File(uri.path ?: "")
                if (file.exists() && file.canRead()) {
                    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Try via ContentResolver
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) return pfd
        } catch (e: Exception) {
            // Permission may have expired or ContentResolver couldn't open
        }

        // Fallback: look for saved internal copy
        val savedFile = findSavedCopy(context, uri, fallbackTitle)
        if (savedFile != null && savedFile.exists() && savedFile.canRead()) {
            try {
                return ParcelFileDescriptor.open(savedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    /**
     * Safely opens an InputStream for both file:// and content:// URIs.
     */
    fun openInputStream(context: Context, uri: Uri, fallbackTitle: String? = null): InputStream? {
        if (uri.scheme == "file") {
            try {
                val file = File(uri.path ?: "")
                if (file.exists() && file.canRead()) {
                    return file.inputStream()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Try via ContentResolver
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) return stream
        } catch (e: Exception) {
            // Permission expired or unavailable
        }

        // Fallback: look for saved internal copy
        val savedFile = findSavedCopy(context, uri, fallbackTitle)
        if (savedFile != null && savedFile.exists() && savedFile.canRead()) {
            try {
                return savedFile.inputStream()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    /**
     * Finds any matching saved file in internal storage by URI hash or title.
     */
    fun findSavedCopy(context: Context, uri: Uri, fallbackTitle: String? = null): File? {
        val dir = File(context.filesDir, SAVED_PDFS_DIR)
        if (!dir.exists()) return null

        val uriHash = (uri.toString().hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        val files = dir.listFiles() ?: return null

        // 1. Direct match by URI hash prefix
        val matchByHash = files.firstOrNull { it.name.startsWith(uriHash) && it.length() > 0 }
        if (matchByHash != null) return matchByHash

        // 2. Match by title if provided
        if (!fallbackTitle.isNullOrBlank()) {
            val cleanTitle = fallbackTitle.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
            val matchByTitle = files.firstOrNull { file ->
                val fileNameClean = file.name.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
                fileNameClean.contains(cleanTitle) && file.length() > 0
            }
            if (matchByTitle != null) return matchByTitle
        }

        return null
    }

    /**
     * Deletes saved internal copy when a book is deleted.
     */
    fun deleteInternalCopy(context: Context, uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists() && file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    file.delete()
                }
            } else {
                findSavedCopy(context, uri)?.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
