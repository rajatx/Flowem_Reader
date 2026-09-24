package com.example.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.example.data.model.ReadingMode
import com.example.data.model.ScrollMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ZoomablePage(
    pageIndex: Int,
    documentUri: String = "",
    readingMode: ReadingMode,
    scrollMode: ScrollMode,
    zoomLevel: Float,
    aspectRatio: Float = 0.707f,
    loadBitmap: suspend (Int, Int, Int) -> Bitmap?,
    onTapCenter: () -> Unit,
    onTapNext: () -> Unit,
    onTapPrev: () -> Unit,
    onZoomChanged: (Boolean) -> Unit = {},
    onAddHighlight: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animScope = rememberCoroutineScope()

    var bitmap by remember(documentUri, pageIndex) { mutableStateOf<Bitmap?>(null) }
    val scaleAnim = remember(documentUri, pageIndex) { Animatable(zoomLevel.coerceIn(1f, 4f)) }
    val offsetXAnim = remember(documentUri, pageIndex) { Animatable(0f) }
    val offsetYAnim = remember(documentUri, pageIndex) { Animatable(0f) }

    var showWordHelpDialog by remember { mutableStateOf(false) }
    var selectedWord by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedHighlightColor by remember { mutableStateOf("#FFE082") }

    LaunchedEffect(zoomLevel) {
        if (zoomLevel in 1.0f..4.0f && kotlin.math.abs(scaleAnim.value - zoomLevel) > 0.05f) {
            scaleAnim.animateTo(zoomLevel, tween(250, easing = FastOutSlowInEasing))
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .testTag("zoomable_page_${documentUri.hashCode()}_$pageIndex"),
        contentAlignment = Alignment.Center
    ) {
        val widthDp = if (maxWidth.isSpecified && maxWidth.value.isFinite()) maxWidth.value else 360f
        val heightDp = if (maxHeight.isSpecified && maxHeight.value.isFinite()) maxHeight.value else (widthDp / aspectRatio)
        val widthPx = (widthDp * 2.2f).toInt().coerceIn(600, 2200)
        val heightPx = (heightDp * 2.2f).toInt().coerceIn(800, 3200)

        LaunchedEffect(documentUri, pageIndex, widthPx, heightPx) {
            bitmap = loadBitmap(pageIndex, widthPx, heightPx)
        }

        if (bitmap != null) {
            val imgBitmap = remember(bitmap) { bitmap!!.asImageBitmap() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(documentUri, pageIndex, scrollMode) {
                        detectTransformAndTapGestures(
                            isZoomed = { scaleAnim.value > 1.05f },
                            onTap = { tapOffset ->
                                val screenWidth = size.width
                                if (scrollMode == ScrollMode.PAGE_BY_PAGE) {
                                    if (tapOffset.x < screenWidth * 0.20f) {
                                        onTapPrev()
                                    } else if (tapOffset.x > screenWidth * 0.80f) {
                                        onTapNext()
                                    } else {
                                        onTapCenter()
                                    }
                                } else {
                                    onTapCenter()
                                }
                            },
                            onDoubleTap = { tapPos ->
                                animScope.launch {
                                    if (scaleAnim.value > 1.08f) {
                                        // Smoothly animate back to normal 1.0x
                                        launch { scaleAnim.animateTo(1.0f, tween(260, easing = FastOutSlowInEasing)) }
                                        launch { offsetXAnim.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
                                        launch { offsetYAnim.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
                                        onZoomChanged(false)
                                    } else {
                                        // Smoothly animate zoom in (2.5x) centered around the tapped location
                                        val targetScale = 2.5f
                                        val maxOffsetX = ((size.width * targetScale) - size.width).coerceAtLeast(0f) / 2f
                                        val maxOffsetY = ((size.height * targetScale) - size.height).coerceAtLeast(0f) / 2f

                                        val tapOffsetCenterX = (size.width / 2f - tapPos.x) * (targetScale - 1f)
                                        val tapOffsetCenterY = (size.height / 2f - tapPos.y) * (targetScale - 1f)

                                        val clampedTargetX = tapOffsetCenterX.coerceIn(-maxOffsetX, maxOffsetX)
                                        val clampedTargetY = tapOffsetCenterY.coerceIn(-maxOffsetY, maxOffsetY)

                                        launch { scaleAnim.animateTo(targetScale, tween(280, easing = FastOutSlowInEasing)) }
                                        launch { offsetXAnim.animateTo(clampedTargetX, tween(280, easing = FastOutSlowInEasing)) }
                                        launch { offsetYAnim.animateTo(clampedTargetY, tween(280, easing = FastOutSlowInEasing)) }
                                        onZoomChanged(true)
                                    }
                                }
                            },
                            onLongPress = {
                                selectedWord = "Passage on Page ${pageIndex + 1}"
                                noteText = ""
                                showWordHelpDialog = true
                            },
                            onTransform = { centroid, pan, zoom ->
                                val currentScale = scaleAnim.value
                                val newScale = (currentScale * zoom).coerceIn(0.75f, 4.5f)

                                val maxOffsetX = ((size.width * newScale) - size.width).coerceAtLeast(0f) / 2f
                                val maxOffsetY = ((size.height * newScale) - size.height).coerceAtLeast(0f) / 2f

                                val center = Offset(size.width / 2f, size.height / 2f)
                                val centroidRel = centroid - center
                                val zoomDelta = newScale - currentScale
                                val centroidShift = centroidRel * (-zoomDelta / currentScale.coerceAtLeast(0.01f))

                                val targetX = if (newScale > 1.0f) {
                                    (offsetXAnim.value + pan.x + centroidShift.x).coerceIn(-maxOffsetX, maxOffsetX)
                                } else 0f

                                val targetY = if (newScale > 1.0f) {
                                    (offsetYAnim.value + pan.y + centroidShift.y).coerceIn(-maxOffsetY, maxOffsetY)
                                } else 0f

                                animScope.launch {
                                    scaleAnim.snapTo(newScale)
                                    offsetXAnim.snapTo(targetX)
                                    offsetYAnim.snapTo(targetY)
                                }
                                if (newScale > 1.05f) {
                                    onZoomChanged(true)
                                }
                            },
                            onTransformEnd = {
                                animScope.launch {
                                    if (scaleAnim.value < 1.05f) {
                                        // Bounce back to 1.0x
                                        launch { scaleAnim.animateTo(1.0f, tween(240, easing = FastOutSlowInEasing)) }
                                        launch { offsetXAnim.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
                                        launch { offsetYAnim.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
                                        onZoomChanged(false)
                                    } else if (scaleAnim.value > 4.0f) {
                                        val maxOffsetX = ((size.width * 4.0f) - size.width).coerceAtLeast(0f) / 2f
                                        val maxOffsetY = ((size.height * 4.0f) - size.height).coerceAtLeast(0f) / 2f
                                        launch { scaleAnim.animateTo(4.0f, tween(240, easing = FastOutSlowInEasing)) }
                                        launch { offsetXAnim.animateTo(offsetXAnim.value.coerceIn(-maxOffsetX, maxOffsetX), tween(240)) }
                                        launch { offsetYAnim.animateTo(offsetYAnim.value.coerceIn(-maxOffsetY, maxOffsetY), tween(240)) }
                                        onZoomChanged(true)
                                    } else {
                                        // Settle offsets within valid bounds
                                        val maxOffsetX = ((size.width * scaleAnim.value) - size.width).coerceAtLeast(0f) / 2f
                                        val maxOffsetY = ((size.height * scaleAnim.value) - size.height).coerceAtLeast(0f) / 2f
                                        val clampedX = offsetXAnim.value.coerceIn(-maxOffsetX, maxOffsetX)
                                        val clampedY = offsetYAnim.value.coerceIn(-maxOffsetY, maxOffsetY)
                                        if (clampedX != offsetXAnim.value) {
                                            launch { offsetXAnim.animateTo(clampedX, tween(200)) }
                                        }
                                        if (clampedY != offsetYAnim.value) {
                                            launch { offsetYAnim.animateTo(clampedY, tween(200)) }
                                        }
                                        onZoomChanged(true)
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val isPageByPage = scrollMode == ScrollMode.PAGE_BY_PAGE
                Surface(
                    shape = if (isPageByPage) RoundedCornerShape(4.dp) else RoundedCornerShape(0.dp),
                    shadowElevation = if (isPageByPage) 3.dp else 0.dp,
                    color = readingMode.backgroundColor,
                    modifier = Modifier
                        .fillMaxSize(if (isPageByPage) 0.985f else 1.0f)
                        .graphicsLayer(
                            scaleX = scaleAnim.value,
                            scaleY = scaleAnim.value,
                            translationX = offsetXAnim.value,
                            translationY = offsetYAnim.value
                        )
                ) {
                    Image(
                        bitmap = imgBitmap,
                        contentDescription = "Page ${pageIndex + 1}",
                        colorFilter = readingMode.getColorFilter(),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Helpful Floating Reset Button when zoomed
                if (scaleAnim.value > 1.15f) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                animScope.launch {
                                    launch { scaleAnim.animateTo(1.0f, tween(260, easing = FastOutSlowInEasing)) }
                                    launch { offsetXAnim.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
                                    launch { offsetYAnim.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
                                    onZoomChanged(false)
                                }
                            }
                            .testTag("zoom_reset_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${(scaleAnim.value * 100).toInt()}% • Reset",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }

    // Word Help & Annotation Dialog
    if (showWordHelpDialog) {
        AlertDialog(
            onDismissRequest = { showWordHelpDialog = false },
            title = {
                Text("Page Annotation & Word Help", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Actions for selected passage on Page ${pageIndex + 1}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Color palette chips for highlighting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Highlight color:", style = MaterialTheme.typography.bodySmall)
                        listOf(
                            "#FFE082" to "Yellow",
                            "#A5D6A7" to "Green",
                            "#90CAF9" to "Blue"
                        ).forEach { (hex, _) ->
                            val isSelected = selectedHighlightColor == hex
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedHighlightColor = hex }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Add optional note") },
                        placeholder = { Text("Thoughts on this section...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )

                    // Quick tools: Copy, Define / Web Search
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("PDF Text", selectedWord))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    showWordHelpDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", fontSize = 12.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                                        putExtra(android.app.SearchManager.QUERY, selectedWord)
                                    }
                                    try {
                                        context.startActivity(searchIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Search unavailable", Toast.LENGTH_SHORT).show()
                                    }
                                    showWordHelpDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Look Up", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddHighlight(selectedWord, selectedHighlightColor, noteText.ifBlank { null })
                        Toast.makeText(context, "Saved highlight", Toast.LENGTH_SHORT).show()
                        showWordHelpDialog = false
                    }
                ) {
                    Text("Save Highlight")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWordHelpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Unified pointer gesture detector that flawlessly balances:
 * 1. Multi-finger pinch-to-zoom and pan with centroid tracking
 * 2. Single-finger pan on already-zoomed pages
 * 3. Animated Double-tap to zoom in / out
 * 4. Tap to turn page or show controls
 * 5. Long-press to open page annotation
 */
private suspend fun PointerInputScope.detectTransformAndTapGestures(
    isZoomed: () -> Boolean,
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onTransformEnd: () -> Unit
) {
    coroutineScope {
        var lastTapTime = 0L
        var lastTapPosition = Offset.Zero
        var pendingTapJob: Job? = null

        awaitEachGesture {
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val downPosition = firstDown.position
            val touchSlop = viewConfiguration.touchSlop

            var isTransforming = false
            var isLongPressed = false
            var hasMovedPastSlop = false
            var totalPan = Offset.Zero

            val currentlyZoomed = isZoomed()

            // If a gesture begins while zoomed, cancel any pending tap immediately
            if (currentlyZoomed) {
                pendingTapJob?.cancel()
            }

            // Launch long-press timer if not currently zoomed
            val longPressJob = if (!currentlyZoomed) {
                launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    if (!hasMovedPastSlop && !isTransforming) {
                        isLongPressed = true
                        pendingTapJob?.cancel()
                        onLongPress(downPosition)
                    }
                }
            } else null

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val pressedList = event.changes.filter { it.pressed }
                    if (pressedList.isEmpty()) {
                        break
                    }

                    if (pressedList.size >= 2) {
                        // Multi-finger pinch to zoom!
                        longPressJob?.cancel()
                        pendingTapJob?.cancel()
                        isTransforming = true

                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)

                        onTransform(centroid, pan, zoom)
                        event.changes.forEach { it.consume() }
                    } else if (isTransforming) {
                        // Seamless continuity when 1 finger remains during transform
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)
                        onTransform(centroid, pan, 1f)
                        event.changes.forEach { it.consume() }
                    } else if (currentlyZoomed) {
                        // 1 finger panning on an already zoomed page
                        val pan = event.calculatePan()
                        totalPan += pan
                        if (!hasMovedPastSlop && totalPan.getDistance() > touchSlop) {
                            hasMovedPastSlop = true
                            isTransforming = true
                            longPressJob?.cancel()
                            pendingTapJob?.cancel()
                        }
                        if (hasMovedPastSlop) {
                            onTransform(pressedList.first().position, pan, 1f)
                            event.changes.forEach { it.consume() }
                        }
                    } else {
                        // 1 finger normal swipe check
                        val pan = event.calculatePan()
                        totalPan += pan
                        if (totalPan.getDistance() > touchSlop) {
                            hasMovedPastSlop = true
                            longPressJob?.cancel()
                        }
                    }
                }
            } finally {
                longPressJob?.cancel()
            }

            if (isTransforming) {
                onTransformEnd()
            } else if (!hasMovedPastSlop && !isLongPressed) {
                val now = System.currentTimeMillis()
                val timeSinceLast = now - lastTapTime
                val distFromLast = (downPosition - lastTapPosition).getDistance()

                if (timeSinceLast in 40..350 && distFromLast < touchSlop * 3) {
                    // Double tap confirmed! Cancel pending tap and smoothly animate zoom
                    pendingTapJob?.cancel()
                    lastTapTime = 0L
                    lastTapPosition = Offset.Zero
                    onDoubleTap(downPosition)
                } else {
                    // Potential single tap - wait 260ms to verify user is not double-tapping
                    lastTapTime = now
                    lastTapPosition = downPosition
                    pendingTapJob?.cancel()
                    pendingTapJob = launch {
                        delay(260)
                        onTap(downPosition)
                    }
                }
            }
        }
    }
}
