package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.db.BookEntity
import com.example.ui.library.ContinueReadingCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun continue_reading_card_screenshot() {
        val sampleBook = BookEntity(
            uriString = "content://sample/book.pdf",
            title = "The Art of Mindful Reading",
            pageCount = 180,
            currentPage = 42
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                ContinueReadingCard(
                    book = sampleBook,
                    onResume = {},
                    onDismiss = {},
                    onToggleFinished = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
