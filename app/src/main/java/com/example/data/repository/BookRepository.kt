package com.example.data.repository

import com.example.data.db.BookDao
import com.example.data.db.BookEntity
import com.example.data.db.BookmarkEntity
import com.example.data.db.HighlightEntity
import com.example.data.db.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class BookRepository(private val bookDao: BookDao) {

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    fun getBook(uri: String): Flow<BookEntity?> = bookDao.getBookByUri(uri)

    suspend fun getBookSync(uri: String): BookEntity? = bookDao.getBookByUriSync(uri)

    suspend fun upsertBook(book: BookEntity) {
        bookDao.insertOrUpdateBook(book)
    }

    suspend fun updateProgress(
        uri: String,
        currentPage: Int,
        pageCount: Int,
        zoom: Float = 1.0f,
        readingMode: String? = null,
        scrollMode: String? = null
    ) {
        val existing = bookDao.getBookByUriSync(uri)
        if (existing != null) {
            val updated = existing.copy(
                currentPage = currentPage,
                pageCount = if (pageCount > 0) pageCount else existing.pageCount,
                lastOpenedTimestamp = System.currentTimeMillis(),
                lastZoomLevel = zoom,
                readingMode = readingMode ?: existing.readingMode,
                scrollMode = scrollMode ?: existing.scrollMode,
                isFinished = if (pageCount > 0 && currentPage >= pageCount - 1) true else existing.isFinished
            )
            bookDao.insertOrUpdateBook(updated)
        }
    }

    suspend fun toggleFinished(uri: String) {
        val existing = bookDao.getBookByUriSync(uri)
        if (existing != null) {
            bookDao.insertOrUpdateBook(existing.copy(isFinished = !existing.isFinished))
        }
    }

    suspend fun deleteBook(book: BookEntity) {
        bookDao.deleteBook(book)
    }

    suspend fun clearAllBooks() {
        bookDao.clearAllBooks()
    }

    // Bookmarks
    fun getBookmarks(bookUri: String): Flow<List<BookmarkEntity>> =
        bookDao.getBookmarksForBook(bookUri)

    suspend fun addBookmark(bookUri: String, pageNumber: Int, title: String) {
        bookDao.insertBookmark(
            BookmarkEntity(
                bookUri = bookUri,
                pageNumber = pageNumber,
                title = title
            )
        )
    }

    suspend fun removeBookmark(id: Long) {
        bookDao.deleteBookmarkById(id)
    }

    suspend fun toggleBookmark(bookUri: String, pageNumber: Int) {
        val bookmarks = bookDao.getBookByUriSync(bookUri)
        // Check if bookmarked
        bookDao.deleteBookmarkForPage(bookUri, pageNumber)
    }

    fun isPageBookmarked(bookUri: String, pageNumber: Int): Flow<Boolean> =
        bookDao.isPageBookmarked(bookUri, pageNumber)

    suspend fun addBookmarkIfNotExist(bookUri: String, pageNumber: Int, title: String) {
        bookDao.insertBookmark(
            BookmarkEntity(
                bookUri = bookUri,
                pageNumber = pageNumber,
                title = title
            )
        )
    }

    suspend fun removeBookmarkForPage(bookUri: String, pageNumber: Int) {
        bookDao.deleteBookmarkForPage(bookUri, pageNumber)
    }

    // Highlights
    fun getHighlights(bookUri: String): Flow<List<HighlightEntity>> =
        bookDao.getHighlightsForBook(bookUri)

    suspend fun addHighlight(
        bookUri: String,
        pageNumber: Int,
        selectedText: String,
        colorHex: String,
        note: String? = null
    ) {
        bookDao.insertHighlight(
            HighlightEntity(
                bookUri = bookUri,
                pageNumber = pageNumber,
                selectedText = selectedText,
                colorHex = colorHex,
                note = note
            )
        )
    }

    suspend fun removeHighlight(id: Long) {
        bookDao.deleteHighlightById(id)
    }

    suspend fun updateHighlightNote(id: Long, note: String) {
        bookDao.updateHighlightNote(id, note)
    }

    // Reading statistics
    suspend fun recordReadingSession(bookUri: String, seconds: Long) {
        if (seconds <= 0) return
        val todayEpochDay = LocalDate.now().toEpochDay()
        bookDao.insertReadingSession(
            ReadingSessionEntity(
                bookUri = bookUri,
                epochDay = todayEpochDay,
                durationSeconds = seconds
            )
        )
        // Also update book total duration
        val book = bookDao.getBookByUriSync(bookUri)
        if (book != null) {
            bookDao.insertOrUpdateBook(
                book.copy(totalReadingSeconds = book.totalReadingSeconds + seconds)
            )
        }
    }

    val todayReadingSeconds: Flow<Long> =
        bookDao.getTodayReadingSeconds(LocalDate.now().toEpochDay()).map { it ?: 0L }

    val weeklyReadingSeconds: Flow<Long> =
        bookDao.getWeeklyReadingSeconds(LocalDate.now().minusDays(7).toEpochDay()).map { it ?: 0L }
}
