package com.example.data.pdf

import android.content.Context
import android.net.Uri
import com.example.data.model.ChapterItem
import com.example.data.model.SearchResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class PdfExtractor(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (ignored: Exception) {}
    }

    suspend fun extractTableOfContents(uri: Uri): List<ChapterItem> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<ChapterItem>()
        var doc: PDDocument? = null
        try {
            val inputStream: InputStream = PdfFileHelper.openInputStream(context, uri) ?: return@withContext emptyList()
            doc = PDDocument.load(inputStream)
            val outline = doc.documentCatalog.documentOutline
            if (outline != null) {
                for (item in outline.children()) {
                    val chapter = parseOutlineItem(item, doc, 0)
                    if (chapter != null) {
                        chapters.add(chapter)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                doc?.close()
            } catch (ignored: Exception) {}
        }
        return@withContext chapters
    }

    private fun parseOutlineItem(item: PDOutlineItem, doc: PDDocument, level: Int): ChapterItem? {
        val title = item.title?.trim() ?: return null
        var targetPage = -1

        try {
            val dest = item.destination
            if (dest is PDPageDestination) {
                targetPage = dest.retrievePageNumber()
                if (targetPage < 0) {
                    val pageObj = dest.page
                    if (pageObj != null) {
                        val idx = doc.pages.indexOf(pageObj)
                        if (idx >= 0) targetPage = idx
                    }
                }
            }
            if (targetPage < 0 && item.action is PDActionGoTo) {
                val actionDest = (item.action as PDActionGoTo).destination
                if (actionDest is PDPageDestination) {
                    targetPage = actionDest.retrievePageNumber()
                    if (targetPage < 0) {
                        val pageObj = actionDest.page
                        if (pageObj != null) {
                            val idx = doc.pages.indexOf(pageObj)
                            if (idx >= 0) targetPage = idx
                        }
                    }
                }
            }
            if (targetPage < 0) {
                val foundPage = item.findDestinationPage(doc)
                if (foundPage != null) {
                    val idx = doc.pages.indexOf(foundPage)
                    if (idx >= 0) targetPage = idx
                }
            }
        } catch (e: Exception) {
            targetPage = -1
        }

        val childChapters = mutableListOf<ChapterItem>()
        try {
            for (child in item.children()) {
                val parsedChild = parseOutlineItem(child, doc, level + 1)
                if (parsedChild != null) {
                    childChapters.add(parsedChild)
                }
            }
        } catch (ignored: Exception) {}

        val finalPage = if (targetPage >= 0) {
            targetPage.coerceIn(0, (doc.numberOfPages - 1).coerceAtLeast(0))
        } else {
            childChapters.firstOrNull()?.pageIndex ?: 0
        }

        return ChapterItem(
            title = title,
            pageIndex = finalPage,
            level = level,
            children = childChapters
        )
    }

    suspend fun extractTextForPage(uri: Uri, pageIndex: Int): String = withContext(Dispatchers.IO) {
        var doc: PDDocument? = null
        try {
            val inputStream: InputStream = PdfFileHelper.openInputStream(context, uri) ?: return@withContext ""
            doc = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val pdfBoxPage = pageIndex + 1 // PDFBox is 1-indexed
            stripper.startPage = pdfBoxPage
            stripper.endPage = pdfBoxPage
            val text = stripper.getText(doc) ?: ""
            return@withContext text.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext ""
        } finally {
            try {
                doc?.close()
            } catch (ignored: Exception) {}
        }
    }

    suspend fun searchInPdf(
        uri: Uri,
        query: String,
        totalPages: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<SearchResult>()
        var doc: PDDocument? = null

        try {
            val inputStream: InputStream = PdfFileHelper.openInputStream(context, uri) ?: return@withContext emptyList()
            doc = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val cleanQuery = query.trim().lowercase()

            val maxPagesToSearch = minOf(totalPages, doc.numberOfPages)
            for (i in 0 until maxPagesToSearch) {
                val pageNum = i + 1
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val pageText = stripper.getText(doc) ?: ""

                if (pageText.isNotEmpty()) {
                    var matchIdx = pageText.indexOf(cleanQuery, 0, ignoreCase = true)
                    while (matchIdx >= 0) {
                        val snippetStart = (matchIdx - 35).coerceAtLeast(0)
                        val snippetEnd = (matchIdx + cleanQuery.length + 35).coerceAtMost(pageText.length)
                        val snippet = "..." + pageText.substring(snippetStart, snippetEnd).replace("\n", " ").trim() + "..."
                        results.add(
                            SearchResult(
                                pageIndex = i,
                                pageNumber = pageNum,
                                snippet = snippet,
                                startIndex = matchIdx,
                                length = cleanQuery.length
                            )
                        )
                        matchIdx = pageText.indexOf(cleanQuery, matchIdx + cleanQuery.length, ignoreCase = true)
                    }
                }
                onProgress(i + 1, maxPagesToSearch)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                doc?.close()
            } catch (ignored: Exception) {}
        }
        return@withContext results
    }
}
