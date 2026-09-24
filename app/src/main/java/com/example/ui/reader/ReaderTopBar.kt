package com.example.ui.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.example.data.model.ReadingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    currentPage: Int = 0,
    totalPages: Int = 0,
    readingMode: ReadingMode,
    isBookmarked: Boolean,
    isAutoScrolling: Boolean,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onCycleReadingMode: () -> Unit = {},
    onOpenGoToPage: () -> Unit = {},
    onToggleBookmark: () -> Unit,
    onStartReadAloud: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onToggleRotationLock: () -> Unit,
    onSharePdf: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (totalPages > 0) {
                    val percent = (((currentPage + 1).toFloat() / totalPages) * 100).toInt()
                    Text(
                        text = "Page ${currentPage + 1} of $totalPages • $percent%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = readingMode.contentColor.copy(alpha = 0.75f)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Library"
                )
            }
        },
        actions = {
            IconButton(onClick = onSearchClick, modifier = Modifier.testTag("reader_search_button")) {
                Icon(Icons.Default.Search, contentDescription = "Search text")
            }

            IconButton(onClick = onCycleReadingMode, modifier = Modifier.testTag("reader_cycle_mode_button")) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = "Reading Mode: ${readingMode.title}"
                )
            }

            IconButton(onClick = onToggleBookmark, modifier = Modifier.testTag("reader_bookmark_button")) {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark page",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            Box {
                IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.testTag("reader_more_menu")) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Go to Page...") },
                        leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onOpenGoToPage()
                        },
                        modifier = Modifier.testTag("reader_menu_go_to_page")
                    )
                    DropdownMenuItem(
                        text = { Text("Read Aloud (TTS)") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onStartReadAloud()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isAutoScrolling) "Stop Auto-scroll" else "Auto-scroll") },
                        leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onToggleAutoScroll()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Lock Screen Orientation") },
                        leadingIcon = { Icon(Icons.Default.ScreenRotation, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onToggleRotationLock()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share PDF File") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onSharePdf()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = readingMode.barColor.copy(alpha = 0.95f),
            titleContentColor = readingMode.contentColor,
            navigationIconContentColor = readingMode.contentColor,
            actionIconContentColor = readingMode.contentColor
        ),
        modifier = Modifier.fillMaxWidth().testTag("reader_top_bar")
    )
}
