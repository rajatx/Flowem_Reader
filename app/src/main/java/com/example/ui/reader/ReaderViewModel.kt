package com.example.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PdfReaderApp
import com.example.data.db.BookEntity
import com.example.data.db.BookmarkEntity
import com.example.data.db.HighlightEntity
import com.example.data.model.AppSettings
import com.example.data.model.ChapterItem
import com.example.data.model.ReadingMode
import com.example.data.model.ScrollMode
import com.example.data.model.SearchResult
import com.example.data.pdf.PdfExtractor
import com.example.data.pdf.PdfFileHelper
import com.example.data.pdf.PdfRendererEngine
import com.example.data.repository.BookRepository
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Stack

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: BookEntity? = null,
    val totalPages: Int = 1,
    val currentPage: Int = 0,
    val readingMode: ReadingMode = ReadingMode.LIGHT,
    val scrollMode: ScrollMode = ScrollMode.VERTICAL,
    val zoomLevel: Float = 1.0f,
    val warmFilterStrength: Float = 0.0f,
    val brightness: Float = 0.8f, // 0.0 to 1.0
    val autoCropMargins: Boolean = false,
    val isControlsVisible: Boolean = true,
    val canJumpBack: Boolean = false,
    val isAutoScrolling: Boolean = false,
    val autoScrollSpeed: Float = 1.0f,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val currentSearchIndex: Int = -1,
    val isSearching: Boolean = false,
    val isExtractingChapters: Boolean = true,
    val chapters: List<ChapterItem> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    val isCurrentPageBookmarked: Boolean = false,
    val breakReminderMessage: String? = null,
    val currentSelectedText: String? = null,
    val errorMessage: String? = null
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val bookRepository: BookRepository = (application as PdfReaderApp).bookRepository
    private val settingsRepository: SettingsRepository = (application as PdfReaderApp).settingsRepository
    private val pdfExtractor: PdfExtractor = (application as PdfReaderApp).pdfExtractor

    private var pdfEngine: PdfRendererEngine? = null
    private var uri: Uri? = null

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val appSettings: StateFlow<AppSettings> = settingsRepository.settings

    private val jumpHistory = Stack<Int>()
    private var readingStartTime = System.currentTimeMillis()
    private var lastInteractionTime = System.currentTimeMillis()
    private var autoScrollJob: Job? = null
    private var breakReminderJob: Job? = null
    private var initJob: Job? = null
    private var tocJob: Job? = null
    private var bookmarksJob: Job? = null
    private var highlightsJob: Job? = null

    fun initialize(uriString: String) {
        val parsedUri = Uri.parse(uriString)

        // If switching from another book, record session for the previous book
        val previousUri = uri
        if (previousUri != null && previousUri.toString() != uriString) {
            val seconds = (System.currentTimeMillis() - readingStartTime) / 1000L
            if (seconds > 0) {
                viewModelScope.launch {
                    bookRepository.recordReadingSession(previousUri.toString(), seconds)
                }
            }
        }

        // Cancel all ongoing jobs for previous book
        initJob?.cancel()
        tocJob?.cancel()
        bookmarksJob?.cancel()
        highlightsJob?.cancel()
        autoScrollJob?.cancel()
        autoScrollJob = null
        breakReminderJob?.cancel()
        breakReminderJob = null
        jumpHistory.clear()

        // Close previous engine to free resources and prevent stale page renders
        pdfEngine?.close()
        pdfEngine = null

        this.uri = parsedUri
        readingStartTime = System.currentTimeMillis()
        lastInteractionTime = System.currentTimeMillis()

        // Explicitly set state to loading and reset document-specific fields
        _uiState.value = ReaderUiState(
            isLoading = true,
            totalPages = 1,
            currentPage = 0,
            chapters = emptyList(),
            bookmarks = emptyList(),
            highlights = emptyList(),
            isCurrentPageBookmarked = false,
            searchResults = emptyList(),
            currentSearchIndex = -1,
            isSearchActive = false,
            searchQuery = "",
            isAutoScrolling = false
        )

        initJob = viewModelScope.launch {
            val appSettings = settingsRepository.settings.first()
            val existingBook = bookRepository.getBookSync(uriString)
            val context = getApplication<Application>()

            // Ensure we have a persistent copy in app storage if it was a content:// URI
            var effectiveUri = parsedUri
            if (parsedUri.scheme == "content") {
                val copied = PdfFileHelper.copyToInternalStorage(
                    context,
                    parsedUri,
                    existingBook?.title ?: "Document.pdf"
                )
                if (copied != parsedUri) {
                    effectiveUri = copied
                    if (existingBook != null && existingBook.uriString != copied.toString()) {
                        bookRepository.deleteBook(existingBook)
                        bookRepository.upsertBook(existingBook.copy(uriString = copied.toString()))
                    }
                }
            }

            var engine = PdfRendererEngine(context, effectiveUri, existingBook?.title)
            var opened = engine.open()

            // If opening effectiveUri failed, try fallback search in internal storage
            if (!opened) {
                PdfFileHelper.findSavedCopy(context, parsedUri, existingBook?.title)?.let { fallbackFile ->
                    if (fallbackFile.exists() && fallbackFile.canRead()) {
                        val fallbackUri = Uri.fromFile(fallbackFile)
                        engine = PdfRendererEngine(context, fallbackUri, existingBook?.title)
                        opened = engine.open()
                        if (opened) {
                            effectiveUri = fallbackUri
                            if (existingBook != null) {
                                bookRepository.deleteBook(existingBook)
                                bookRepository.upsertBook(existingBook.copy(uriString = fallbackUri.toString()))
                            }
                        }
                    }
                }
            }

            if (opened) {
                uri = effectiveUri
                pdfEngine = engine
                val count = engine.pageCount
                val initialPage = existingBook?.currentPage?.coerceIn(0, (count - 1).coerceAtLeast(0)) ?: 0
                val initialMode = existingBook?.readingMode?.let {
                    try { ReadingMode.valueOf(it) } catch (e: Exception) { appSettings.defaultReadingMode }
                } ?: appSettings.defaultReadingMode
                val initialScroll = existingBook?.scrollMode?.let {
                    try { ScrollMode.valueOf(it) } catch (e: Exception) { appSettings.defaultScrollMode }
                } ?: appSettings.defaultScrollMode

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        book = existingBook,
                        totalPages = count,
                        currentPage = initialPage,
                        readingMode = initialMode,
                        scrollMode = initialScroll,
                        zoomLevel = existingBook?.lastZoomLevel ?: 1.0f,
                        warmFilterStrength = appSettings.warmFilterStrength,
                        autoCropMargins = appSettings.autoCropMargins,
                        errorMessage = null
                    )
                }

                // Load chapters in background
                loadTableOfContents(effectiveUri)
                // Observe bookmarks and highlights
                observeBookmarksAndHighlights(effectiveUri.toString())
                // Start break reminder if enabled
                startBreakReminderTimer(appSettings.breakReminderMinutes)
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Cannot reload document. The system permission for this file expired or it was moved. Please re-open it from your files."
                    )
                }
            }
        }
    }

    private fun loadTableOfContents(uri: Uri) {
        tocJob?.cancel()
        tocJob = viewModelScope.launch {
            _uiState.update { it.copy(isExtractingChapters = true) }
            val toc = pdfExtractor.extractTableOfContents(uri)
            _uiState.update { it.copy(chapters = toc, isExtractingChapters = false) }
        }
    }

    private fun observeBookmarksAndHighlights(bookUri: String) {
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            bookRepository.getBookmarks(bookUri).collect { list ->
                _uiState.update { state ->
                    state.copy(
                        bookmarks = list,
                        isCurrentPageBookmarked = list.any { it.pageNumber == state.currentPage + 1 }
                    )
                }
            }
        }
        highlightsJob?.cancel()
        highlightsJob = viewModelScope.launch {
            bookRepository.getHighlights(bookUri).collect { list ->
                _uiState.update { it.copy(highlights = list) }
            }
        }
    }

    suspend fun getPageBitmap(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        val crop = _uiState.value.autoCropMargins
        return pdfEngine?.renderPage(pageIndex, targetWidth, targetHeight, crop)
    }

    fun getPageAspectRatio(pageIndex: Int): Float {
        return pdfEngine?.getPageAspectRatio(pageIndex) ?: 0.707f
    }

    fun onPageChanged(page: Int) {
        lastInteractionTime = System.currentTimeMillis()
        val total = _uiState.value.totalPages
        if (page in 0 until total && page != _uiState.value.currentPage) {
            _uiState.update { state ->
                state.copy(
                    currentPage = page,
                    isCurrentPageBookmarked = state.bookmarks.any { it.pageNumber == page + 1 }
                )
            }
            saveCurrentProgress()
        }
    }

    fun jumpToPage(page: Int, recordHistory: Boolean = true) {
        val total = _uiState.value.totalPages
        val clamped = page.coerceIn(0, total - 1)
        if (recordHistory && clamped != _uiState.value.currentPage) {
            jumpHistory.push(_uiState.value.currentPage)
        }
        _uiState.update { state ->
            state.copy(
                currentPage = clamped,
                canJumpBack = jumpHistory.isNotEmpty(),
                isCurrentPageBookmarked = state.bookmarks.any { it.pageNumber == clamped + 1 }
            )
        }
        saveCurrentProgress()
    }

    fun jumpBack() {
        if (jumpHistory.isNotEmpty()) {
            val previousPage = jumpHistory.pop()
            _uiState.update { state ->
                state.copy(
                    currentPage = previousPage,
                    canJumpBack = jumpHistory.isNotEmpty(),
                    isCurrentPageBookmarked = state.bookmarks.any { it.pageNumber == previousPage + 1 }
                )
            }
            saveCurrentProgress()
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(isControlsVisible = visible) }
    }

    fun setReadingMode(mode: ReadingMode) {
        _uiState.update { it.copy(readingMode = mode) }
        saveCurrentProgress()
    }

    fun cycleReadingMode() {
        val modes = ReadingMode.values()
        val currentIdx = modes.indexOf(_uiState.value.readingMode)
        val nextMode = modes[(currentIdx + 1) % modes.size]
        setReadingMode(nextMode)
    }

    fun setAppTheme(mode: com.example.data.model.AppThemeMode) {
        settingsRepository.updateThemeMode(mode)
    }

    fun setScrollMode(mode: ScrollMode) {
        _uiState.update { it.copy(scrollMode = mode) }
        saveCurrentProgress()
    }

    fun toggleScrollMode() {
        val nextMode = if (_uiState.value.scrollMode == ScrollMode.PAGE_BY_PAGE) {
            ScrollMode.VERTICAL
        } else {
            ScrollMode.PAGE_BY_PAGE
        }
        setScrollMode(nextMode)
    }

    fun setZoomLevel(zoom: Float) {
        _uiState.update { it.copy(zoomLevel = zoom.coerceIn(1.0f, 3.5f)) }
        saveCurrentProgress()
    }

    fun setWarmFilterStrength(strength: Float) {
        val clamped = strength.coerceIn(0f, 1f)
        _uiState.update { it.copy(warmFilterStrength = clamped) }
        settingsRepository.updateWarmFilterStrength(clamped)
    }

    fun setBrightness(brightness: Float) {
        _uiState.update { it.copy(brightness = brightness.coerceIn(0.01f, 1.0f)) }
    }

    fun toggleAutoCrop() {
        val current = _uiState.value.autoCropMargins
        _uiState.update { it.copy(autoCropMargins = !current) }
        settingsRepository.updateAutoCropMargins(!current)
    }

    fun toggleBookmark() {
        val bookUri = uri?.toString() ?: return
        val currentPg = _uiState.value.currentPage + 1
        val isBookmarked = _uiState.value.isCurrentPageBookmarked

        viewModelScope.launch {
            if (isBookmarked) {
                bookRepository.removeBookmarkForPage(bookUri, currentPg)
            } else {
                bookRepository.addBookmark(bookUri, currentPg, "Page $currentPg")
            }
        }
    }

    fun addHighlight(text: String, colorHex: String, note: String? = null) {
        val bookUri = uri?.toString() ?: return
        val currentPg = _uiState.value.currentPage + 1
        viewModelScope.launch {
            bookRepository.addHighlight(bookUri, currentPg, text, colorHex, note)
        }
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch {
            bookRepository.removeHighlight(id)
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookRepository.removeBookmark(id)
        }
    }

    fun startSearch() {
        _uiState.update { it.copy(isSearchActive = true) }
    }

    fun closeSearch() {
        _uiState.update {
            it.copy(
                isSearchActive = false,
                searchQuery = "",
                searchResults = emptyList(),
                currentSearchIndex = -1
            )
        }
    }

    fun search(query: String) {
        val currentUri = uri ?: return
        val total = _uiState.value.totalPages
        _uiState.update { it.copy(searchQuery = query, isSearching = true) }

        viewModelScope.launch {
            val results = pdfExtractor.searchInPdf(currentUri, query, total)
            _uiState.update {
                it.copy(
                    searchResults = results,
                    isSearching = false,
                    currentSearchIndex = if (results.isNotEmpty()) 0 else -1
                )
            }
            if (results.isNotEmpty()) {
                jumpToPage(results[0].pageIndex, recordHistory = true)
            }
        }
    }

    fun nextSearchResult() {
        val state = _uiState.value
        if (state.searchResults.isEmpty()) return
        val nextIdx = (state.currentSearchIndex + 1) % state.searchResults.size
        _uiState.update { it.copy(currentSearchIndex = nextIdx) }
        jumpToPage(state.searchResults[nextIdx].pageIndex, recordHistory = true)
    }

    fun prevSearchResult() {
        val state = _uiState.value
        if (state.searchResults.isEmpty()) return
        val prevIdx = if (state.currentSearchIndex <= 0) state.searchResults.size - 1 else state.currentSearchIndex - 1
        _uiState.update { it.copy(currentSearchIndex = prevIdx) }
        jumpToPage(state.searchResults[prevIdx].pageIndex, recordHistory = true)
    }

    fun toggleAutoScroll() {
        val currentlyScrolling = _uiState.value.isAutoScrolling
        if (currentlyScrolling) {
            autoScrollJob?.cancel()
            _uiState.update { it.copy(isAutoScrolling = false) }
        } else {
            _uiState.update { it.copy(isAutoScrolling = true) }
            autoScrollJob = viewModelScope.launch {
                while (isActive) {
                    delay(3000L)
                    val next = _uiState.value.currentPage + 1
                    if (next < _uiState.value.totalPages) {
                        onPageChanged(next)
                    } else {
                        _uiState.update { it.copy(isAutoScrolling = false) }
                        break
                    }
                }
            }
        }
    }

    private fun startBreakReminderTimer(minutes: Int) {
        if (minutes <= 0) return
        breakReminderJob?.cancel()
        breakReminderJob = viewModelScope.launch {
            while (isActive) {
                delay(minutes * 60 * 1000L)
                _uiState.update {
                    it.copy(breakReminderMessage = "20-second eye rest: look 20 feet away to relax your eyes.")
                }
            }
        }
    }

    fun dismissBreakReminder() {
        _uiState.update { it.copy(breakReminderMessage = null) }
    }

    fun setSelectedWord(word: String?) {
        _uiState.update { it.copy(currentSelectedText = word) }
    }

    private fun saveCurrentProgress() {
        val bookUri = uri?.toString() ?: return
        val state = _uiState.value
        viewModelScope.launch {
            bookRepository.updateProgress(
                uri = bookUri,
                currentPage = state.currentPage,
                pageCount = state.totalPages,
                zoom = state.zoomLevel,
                readingMode = state.readingMode.name,
                scrollMode = state.scrollMode.name
            )
        }
    }

    fun getExportNotesText(): String {
        val book = _uiState.value.book
        val title = book?.title ?: "PDF Notes"
        val sb = StringBuilder()
        sb.appendLine("Notes & Highlights from: $title")
        sb.appendLine("Exported on ${java.util.Date()}")
        sb.appendLine("=".repeat(40))
        sb.appendLine()

        val bookmarks = _uiState.value.bookmarks
        if (bookmarks.isNotEmpty()) {
            sb.appendLine("BOOKMARKS:")
            for (bm in bookmarks) {
                sb.appendLine("• Page ${bm.pageNumber}: ${bm.title}")
            }
            sb.appendLine()
        }

        val highlights = _uiState.value.highlights
        if (highlights.isNotEmpty()) {
            sb.appendLine("HIGHLIGHTS & NOTES:")
            for (hl in highlights) {
                sb.appendLine("• Page ${hl.pageNumber}: \"${hl.selectedText}\"")
                if (!hl.note.isNullOrBlank()) {
                    sb.appendLine("  Note: ${hl.note}")
                }
            }
        }
        return sb.toString()
    }

    fun closeReader() {
        // Record reading time session
        val seconds = (System.currentTimeMillis() - readingStartTime) / 1000L
        val currentUri = uri
        if (currentUri != null && seconds > 0) {
            viewModelScope.launch {
                bookRepository.recordReadingSession(currentUri.toString(), seconds)
            }
        }
        autoScrollJob?.cancel()
        autoScrollJob = null
        breakReminderJob?.cancel()
        breakReminderJob = null
        initJob?.cancel()
        tocJob?.cancel()
        bookmarksJob?.cancel()
        highlightsJob?.cancel()
        pdfEngine?.close()
        pdfEngine = null
        uri = null
        _uiState.value = ReaderUiState(isLoading = true)
    }

    override fun onCleared() {
        super.onCleared()
        closeReader()
    }
}
