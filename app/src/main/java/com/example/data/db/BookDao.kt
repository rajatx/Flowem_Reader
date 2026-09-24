package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY lastOpenedTimestamp DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE uriString = :uriString LIMIT 1")
    fun getBookByUri(uriString: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE uriString = :uriString LIMIT 1")
    suspend fun getBookByUriSync(uriString: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books")
    suspend fun clearAllBooks()

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE bookUri = :bookUri ORDER BY pageNumber ASC")
    fun getBookmarksForBook(bookUri: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookUri = :bookUri AND pageNumber = :pageNumber")
    suspend fun deleteBookmarkForPage(bookUri: String, pageNumber: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE bookUri = :bookUri AND pageNumber = :pageNumber LIMIT 1)")
    fun isPageBookmarked(bookUri: String, pageNumber: Int): Flow<Boolean>

    // Highlights & Notes
    @Query("SELECT * FROM highlights WHERE bookUri = :bookUri ORDER BY pageNumber ASC, createdAt ASC")
    fun getHighlightsForBook(bookUri: String): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlightById(id: Long)

    @Query("UPDATE highlights SET note = :note WHERE id = :id")
    suspend fun updateHighlightNote(id: Long, note: String)

    // Reading statistics
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingSession(session: ReadingSessionEntity)

    @Query("SELECT SUM(durationSeconds) FROM reading_sessions WHERE epochDay = :epochDay")
    fun getTodayReadingSeconds(epochDay: Long): Flow<Long?>

    @Query("SELECT SUM(durationSeconds) FROM reading_sessions WHERE epochDay >= :startEpochDay")
    fun getWeeklyReadingSeconds(startEpochDay: Long): Flow<Long?>
}
