package com.example.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReadingMode
import com.example.data.model.ScrollMode

@Composable
fun ReaderBottomBar(
    currentPage: Int,
    totalPages: Int,
    readingMode: ReadingMode,
    scrollMode: ScrollMode = ScrollMode.PAGE_BY_PAGE,
    canJumpBack: Boolean,
    onPageSelected: (Int) -> Unit,
    onJumpBack: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onToggleScrollMode: () -> Unit = {},
    onOpenBookmarks: () -> Unit
) {
    var showJumpDialog by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var scrubPage by remember(currentPage) { mutableStateOf(currentPage.toFloat()) }

    val displayPage = if (isDraggingSlider) scrubPage.toInt().coerceIn(0, (totalPages - 1).coerceAtLeast(0)) else currentPage
    val percent = if (totalPages > 0) {
        (((displayPage + 1).toFloat() / totalPages) * 100).toInt()
    } else 0

    Surface(
        color = readingMode.barColor.copy(alpha = 0.96f),
        contentColor = readingMode.contentColor,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reader_bottom_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Slider to scrub through pages
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canJumpBack) {
                    IconButton(
                        onClick = onJumpBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Return to previous page",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Slider(
                    value = if (isDraggingSlider) scrubPage else currentPage.toFloat(),
                    onValueChange = {
                        isDraggingSlider = true
                        scrubPage = it
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        onPageSelected(scrubPage.toInt().coerceIn(0, (totalPages - 1).coerceAtLeast(0)))
                    },
                    valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("page_scrub_slider")
                )
            }

            // Bottom Navigation Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Table of Contents Button
                IconButton(onClick = onOpenToc, modifier = Modifier.testTag("toc_button")) {
                    Icon(Icons.Default.ListAlt, contentDescription = "Table of contents")
                }

                // Page Navigation / Indicator / Jump Dialog Trigger
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Previous Page button
                    IconButton(
                        onClick = { if (currentPage > 0) onPageSelected(currentPage - 1) },
                        enabled = currentPage > 0,
                        modifier = Modifier.size(36.dp).testTag("prev_page_button")
                    ) {
                        Text(
                            "‹",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentPage > 0) readingMode.contentColor else readingMode.contentColor.copy(alpha = 0.3f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                showJumpDialog = true
                            }
                            .testTag("page_indicator_button")
                    ) {
                        Text(
                            text = "Page ${displayPage + 1} of $totalPages  ($percent%)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = readingMode.contentColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    // Next Page button
                    IconButton(
                        onClick = { if (currentPage < totalPages - 1) onPageSelected(currentPage + 1) },
                        enabled = currentPage < totalPages - 1,
                        modifier = Modifier.size(36.dp).testTag("next_page_button")
                    ) {
                        Text(
                            "›",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentPage < totalPages - 1) readingMode.contentColor else readingMode.contentColor.copy(alpha = 0.3f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Toggle Reading Layout (Horizontal Swipe vs Vertical Continuous)
                    IconButton(
                        onClick = onToggleScrollMode,
                        modifier = Modifier.testTag("toggle_scroll_mode_button")
                    ) {
                        Icon(
                            if (scrollMode == ScrollMode.VERTICAL) Icons.Default.SwapVert else Icons.Default.ViewCarousel,
                            contentDescription = if (scrollMode == ScrollMode.VERTICAL) "Switch to Horizontal Swipe" else "Switch to Vertical Scroll",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Display & Comfort "Aa" Button
                    IconButton(onClick = onOpenDisplaySettings, modifier = Modifier.testTag("display_settings_button")) {
                        Icon(Icons.Default.TextFields, contentDescription = "Display settings")
                    }

                    // Bookmarks & Notes Button
                    IconButton(onClick = onOpenBookmarks, modifier = Modifier.testTag("bookmarks_sheet_button")) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks and notes")
                    }
                }
            }
        }
    }

    // Direct Page Number Jump Dialog
    if (showJumpDialog) {
        GoToPageDialog(
            currentPage = currentPage,
            totalPages = totalPages,
            onDismiss = { showJumpDialog = false },
            onPageSelected = { targetPage ->
                onPageSelected(targetPage)
            }
        )
    }
}
