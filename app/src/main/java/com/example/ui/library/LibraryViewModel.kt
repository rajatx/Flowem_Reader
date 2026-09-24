package com.example.ui.library

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PdfReaderApp
import com.example.data.db.BookEntity
import com.example.data.pdf.PdfFileHelper
import com.example.data.pdf.PdfRendererEngine
import com.example.data.pdf.SamplePdfGenerator
import com.example.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOption(val title: String) {
    RECENT("Recent"),
    NAME("Name"),
    PROGRESS("Progress")
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val bookRepository: BookRepository = (application as PdfReaderApp).bookRepository
    private val sortOption = MutableStateFlow(SortOption.RECENT)
    private val searchQuery = MutableStateFlow("")
    private val dismissedContinueReadingUri = MutableStateFlow<String?>(null)

    val currentSort: StateFlow<SortOption> = sortOption
    val currentSearchQuery: StateFlow<String> = searchQuery

    val books: StateFlow<List<BookEntity>> = combine(
        bookRepository.allBooks,
        sortOption,
        searchQuery
    ) { list, sort, query ->
        val filtered = if (query.isBlank()) {
            list
        } else {
            list.filter { it.title.contains(query.trim(), ignoreCase = true) }
        }
        when (sort) {
            SortOption.RECENT -> filtered.sortedByDescending { it.lastOpenedTimestamp }
            SortOption.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortOption.PROGRESS -> filtered.sortedByDescending {
                if (it.pageCount > 0) (it.currentPage.toFloat() / it.pageCount) else 0f
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueReadingBook: StateFlow<BookEntity?> = combine(
        bookRepository.allBooks,
        dismissedContinueReadingUri
    ) { list, dismissedUri ->
        val candidate = list.maxByOrNull { it.lastOpenedTimestamp }
        if (candidate != null && candidate.uriString != dismissedUri) {
            candidate
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayReadingSeconds: StateFlow<Long> = bookRepository.todayReadingSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val weeklyReadingSeconds: StateFlow<Long> = bookRepository.weeklyReadingSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun dismissContinueReading(uriString: String) {
        dismissedContinueReadingUri.value = uriString
    }

    fun setSort(sort: SortOption) {
        sortOption.value = sort
    }

    fun onPdfSelected(uri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            // Query display name
            val fileName = PdfFileHelper.getDisplayName(context, uri)
            val cleanTitle = fileName
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
                .replace("_", " ")

            // Proactively copy to internal app storage so that subsequent library opens NEVER fail!
            val persistentUri = PdfFileHelper.copyToInternalStorage(context, uri, fileName)
            val persistentUriString = persistentUri.toString()

            // Check if already in DB (either by persistent file URI or previous content URI)
            val existing = bookRepository.getBookSync(persistentUriString)
                ?: bookRepository.getBookSync(uri.toString())

            if (existing != null) {
                // If it was stored under old content URI, migrate it to the persistent URI
                if (existing.uriString != persistentUriString) {
                    bookRepository.deleteBook(existing)
                    bookRepository.upsertBook(
                        existing.copy(
                            uriString = persistentUriString,
                            lastOpenedTimestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    bookRepository.upsertBook(
                        existing.copy(lastOpenedTimestamp = System.currentTimeMillis())
                    )
                }
                onComplete(persistentUriString)
                return@launch
            }

            // Inspect PDF and generate thumbnail
            val engine = PdfRendererEngine(context, persistentUri, cleanTitle)
            var pages = 1
            var coverPath: String? = null
            if (engine.open()) {
                pages = engine.pageCount
                coverPath = engine.generateCoverThumbnail()
                engine.close()
            }

            val newBook = BookEntity(
                uriString = persistentUriString,
                title = cleanTitle,
                pageCount = pages,
                currentPage = 0,
                lastOpenedTimestamp = System.currentTimeMillis(),
                coverImagePath = coverPath
            )
            bookRepository.upsertBook(newBook)
            onComplete(persistentUriString)
        }
    }

    fun openSampleBook(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val sampleUri = SamplePdfGenerator.getOrCreateSampleBook(context)
            onPdfSelected(sampleUri, onComplete)
        }
    }

    fun toggleFinished(uri: String) {
        viewModelScope.launch {
            bookRepository.toggleFinished(uri)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            PdfFileHelper.deleteInternalCopy(getApplication(), book.uriString)
            bookRepository.deleteBook(book)
        }
    }
}
