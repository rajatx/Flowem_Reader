package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.reader.ReaderScreen
import com.example.ui.reader.ReaderViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

sealed interface Screen {
    data object Library : Screen
    data class Reader(val uriString: String) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private val libraryViewModel: LibraryViewModel by viewModels()
    private val readerViewModel: ReaderViewModel by viewModels()

    private var currentScreen by mutableStateOf<Screen>(Screen.Library)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        val app = application as PdfReaderApp

        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = settings.themeMode) {
                when (val screen = currentScreen) {
                    is Screen.Library -> {
                        LibraryScreen(
                            viewModel = libraryViewModel,
                            onOpenBook = { uri ->
                                currentScreen = Screen.Reader(uri)
                            },
                            onNavigateSettings = {
                                currentScreen = Screen.Settings
                            }
                        )
                    }
                    is Screen.Reader -> {
                        ReaderScreen(
                            uriString = screen.uriString,
                            viewModel = readerViewModel,
                            onNavigateBack = {
                                currentScreen = Screen.Library
                            }
                        )
                    }
                    is Screen.Settings -> {
                        SettingsScreen(
                            onNavigateBack = {
                                currentScreen = Screen.Library
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        val uri: Uri? = when {
            action == Intent.ACTION_VIEW -> intent.data
            action == Intent.ACTION_SEND && type == "application/pdf" -> {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }

        if (uri != null) {
            libraryViewModel.onPdfSelected(uri) { bookUri ->
                currentScreen = Screen.Reader(bookUri)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val app = application as? PdfReaderApp
        val volumeEnabled = app?.settingsRepository?.settings?.value?.volumeKeysTurnPages ?: false

        if (volumeEnabled && currentScreen is Screen.Reader) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val current = readerViewModel.uiState.value.currentPage
                    val total = readerViewModel.uiState.value.totalPages
                    if (current < total - 1) {
                        readerViewModel.jumpToPage(current + 1, recordHistory = false)
                    }
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    val current = readerViewModel.uiState.value.currentPage
                    if (current > 0) {
                        readerViewModel.jumpToPage(current - 1, recordHistory = false)
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
