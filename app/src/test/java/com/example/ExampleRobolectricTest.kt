package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.BookEntity
import com.example.data.db.BookmarkEntity
import com.example.data.db.HighlightEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Flowem Reader", appName)
    }

    @Test
    fun `test book database insertion and retrieval`() = runBlocking {
        val book = BookEntity(
            uriString = "content://test/book.pdf",
            title = "Test Book",
            pageCount = 120,
            currentPage = 24
        )
        db.bookDao().insertOrUpdateBook(book)

        val retrieved = db.bookDao().getBookByUriSync("content://test/book.pdf")
        assertNotNull(retrieved)
        assertEquals("Test Book", retrieved?.title)
        assertEquals(24, retrieved?.currentPage)
        assertEquals(120, retrieved?.pageCount)
    }

    @Test
    fun `test bookmarks and highlights persistence`() = runBlocking {
        val uri = "content://test/book2.pdf"
        db.bookDao().insertBookmark(BookmarkEntity(bookUri = uri, pageNumber = 5, title = "Important Chapter"))
        db.bookDao().insertHighlight(
            HighlightEntity(
                bookUri = uri,
                pageNumber = 5,
                selectedText = "A calm mind brings deep wisdom.",
                colorHex = "#FFE082",
                note = "Seneca reference"
            )
        )

        val bookmarks = db.bookDao().getBookmarksForBook(uri).first()
        val highlights = db.bookDao().getHighlightsForBook(uri).first()

        assertEquals(1, bookmarks.size)
        assertEquals("Important Chapter", bookmarks[0].title)
        assertEquals(1, highlights.size)
        assertEquals("Seneca reference", highlights[0].note)
    }
}
