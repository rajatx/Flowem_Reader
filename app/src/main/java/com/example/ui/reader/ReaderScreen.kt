package com.example.ui.reader

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ScrollMode
import com.example.service.ReadAloudService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    uriString: String,
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    var showDisplaySettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }

    // Read Aloud Service Binding
    var readAloudService by remember { mutableStateOf<ReadAloudService?>(null) }
    var isTtsActive by remember { mutableStateOf(false) }
    var isTtsPlaying by remember { mutableStateOf(false) }
    var ttsSpeed by remember { mutableStateOf(1.0f) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? ReadAloudService.LocalBinder
                readAloudService = binder?.getService()
                readAloudService?.let { s ->
                    scope.launch {
                        s.playerState.collectLatest { state ->
                            isTtsPlaying = state.isPlaying
                            ttsSpeed = state.speed
                            if (state.isPlaying && state.pageIndex != uiState.currentPage) {
                                viewModel.jumpToPage(state.pageIndex, recordHistory = false)
                            }
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                readAloudService = null
                isTtsActive = false
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, ReadAloudService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        onDispose {
            try {
                context.unbindService(serviceConnection)
            } catch (ignored: Exception) {}
        }
    }

    // Keep screen on
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(uriString) {
        viewModel.initialize(uriString)
    }

    val handleNavigateBack: () -> Unit = {
        viewModel.closeReader()
        onNavigateBack()
    }

    BackHandler {
        if (uiState.isSearchActive) {
            viewModel.closeSearch()
        } else {
            handleNavigateBack()
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (uiState.errorMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Unable to Open Document",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = handleNavigateBack) {
                        Text("Back to Library")
                    }
                }
            }
        }
        return
    }

    val totalPages = uiState.totalPages.coerceAtLeast(1)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = uiState.isControlsVisible && !uiState.isSearchActive,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                ReaderTopBar(
                    title = uiState.book?.title ?: "Document",
                    currentPage = uiState.currentPage,
                    totalPages = totalPages,
                    readingMode = uiState.readingMode,
                    isBookmarked = uiState.isCurrentPageBookmarked,
                    isAutoScrolling = uiState.isAutoScrolling,
                    onBack = handleNavigateBack,
                    onSearchClick = { viewModel.startSearch() },
                    onCycleReadingMode = { viewModel.cycleReadingMode() },
                    onOpenGoToPage = { showGoToPageDialog = true },
                    onToggleBookmark = { viewModel.toggleBookmark() },
                    onStartReadAloud = {
                        val parsedUri = Uri.parse(uriString)
                        val title = uiState.book?.title ?: "PDF Document"
                        val intent = Intent(context, ReadAloudService::class.java)
                        context.startService(intent)
                        readAloudService?.startReading(parsedUri, title, uiState.currentPage, totalPages)
                        isTtsActive = true
                    },
                    onToggleAutoScroll = { viewModel.toggleAutoScroll() },
                    onToggleRotationLock = {
                        activity?.let { act ->
                            act.requestedOrientation = if (act.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }
                    },
                    onSharePdf = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share PDF Document"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                ReaderBottomBar(
                    currentPage = uiState.currentPage,
                    totalPages = totalPages,
                    readingMode = uiState.readingMode,
                    scrollMode = uiState.scrollMode,
                    canJumpBack = uiState.canJumpBack,
                    onPageSelected = { page -> viewModel.jumpToPage(page, recordHistory = true) },
                    onJumpBack = { viewModel.jumpBack() },
                    onOpenToc = { showToc = true },
                    onOpenDisplaySettings = { showDisplaySettings = true },
                    onToggleScrollMode = { viewModel.toggleScrollMode() },
                    onOpenBookmarks = { showBookmarks = true }
                )
            }
        },
        containerColor = uiState.readingMode.backgroundColor
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(uiState.readingMode.backgroundColor)
        ) {
            // Main Page Content
            if (uiState.scrollMode == ScrollMode.PAGE_BY_PAGE) {
                var isCurrentPageZoomed by remember { mutableStateOf(false) }

                val pagerState = rememberPagerState(
                    initialPage = uiState.currentPage,
                    pageCount = { totalPages }
                )

                // Sync pager when opening a new document or changing currentPage in viewModel
                LaunchedEffect(uriString, uiState.currentPage) {
                    if (pagerState.currentPage != uiState.currentPage) {
                        pagerState.scrollToPage(uiState.currentPage)
                    }
                }

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }
                        .distinctUntilChanged()
                        .collectLatest { page ->
                            isCurrentPageZoomed = false
                            if (page != uiState.currentPage) {
                                viewModel.onPageChanged(page)
                            }
                        }
                }

                val flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1)
                )

                HorizontalPager(
                    state = pagerState,
                    key = { page -> "${uriString}_$page" },
                    flingBehavior = flingBehavior,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !isCurrentPageZoomed,
                    beyondViewportPageCount = 1
                ) { page ->
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    val pageAspectRatio = remember(uriString, page) { viewModel.getPageAspectRatio(page) }
                    ZoomablePage(
                        pageIndex = page,
                        documentUri = uriString,
                        readingMode = uiState.readingMode,
                        scrollMode = uiState.scrollMode,
                        zoomLevel = uiState.zoomLevel,
                        aspectRatio = pageAspectRatio,
                        loadBitmap = { p, w, h -> viewModel.getPageBitmap(p, w, h) },
                        onTapCenter = { viewModel.toggleControls() },
                        onTapNext = {
                            if (pagerState.currentPage < totalPages - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        onTapPrev = {
                            if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        },
                        onZoomChanged = { zoomed ->
                            if (page == pagerState.currentPage) {
                                isCurrentPageZoomed = zoomed
                            }
                        },
                        onAddHighlight = { text, color, note -> viewModel.addHighlight(text, color, note) },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val absOffset = kotlin.math.abs(pageOffset)
                                if (absOffset < 1.0f) {
                                    // Kindle-like soft depth scaling and page shadow
                                    val scale = 1.0f - (0.04f * absOffset)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = (1.0f - (0.15f * absOffset)).coerceIn(0f, 1f)
                                    // Subtle realistic page curl rotation around Y axis
                                    rotationY = (-pageOffset * 10f).coerceIn(-18f, 18f)
                                    cameraDistance = 14f * density
                                }
                            }
                    )
                }
            } else {
                // Vertical Continuous Scroll
                var isVerticalPageZoomed by remember { mutableStateOf(false) }
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = uiState.currentPage)

                LaunchedEffect(uriString, uiState.currentPage) {
                    if (listState.firstVisibleItemIndex != uiState.currentPage) {
                        listState.scrollToItem(uiState.currentPage)
                    }
                }

                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .distinctUntilChanged()
                        .collectLatest { page ->
                            isVerticalPageZoomed = false
                            if (page != uiState.currentPage) {
                                viewModel.onPageChanged(page)
                            }
                        }
                }

                LazyColumn(
                    state = listState,
                    userScrollEnabled = !isVerticalPageZoomed,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(totalPages, key = { page -> "${uriString}_$page" }) { page ->
                        val pageAspectRatio = remember(uriString, page) { viewModel.getPageAspectRatio(page) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            ZoomablePage(
                                pageIndex = page,
                                documentUri = uriString,
                                readingMode = uiState.readingMode,
                                scrollMode = uiState.scrollMode,
                                zoomLevel = uiState.zoomLevel,
                                aspectRatio = pageAspectRatio,
                                loadBitmap = { p, w, h -> viewModel.getPageBitmap(p, w, h) },
                                onTapCenter = { viewModel.toggleControls() },
                                onTapNext = {},
                                onTapPrev = {},
                                onZoomChanged = { zoomed -> isVerticalPageZoomed = zoomed },
                                onAddHighlight = { text, color, note -> viewModel.addHighlight(text, color, note) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(pageAspectRatio)
                            )
                        }
                    }
                }
            }

            // Warm light filter overlay
            if (uiState.warmFilterStrength > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE5A044).copy(alpha = uiState.warmFilterStrength * 0.32f))
                )
            }

            // Night-time ultra-low brightness scrim (dimming below system minimum)
            if (uiState.brightness < 0.35f) {
                val scrimAlpha = ((0.35f - uiState.brightness) / 0.35f) * 0.75f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                )
            }

            // Top Search Bar
            AnimatedVisibility(
                visible = uiState.isSearchActive,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                SearchOverlay(
                    query = uiState.searchQuery,
                    results = uiState.searchResults,
                    currentIndex = uiState.currentSearchIndex,
                    isSearching = uiState.isSearching,
                    onQueryChange = { query -> viewModel.search(query) },
                    onSearch = { query -> viewModel.search(query) },
                    onNext = { viewModel.nextSearchResult() },
                    onPrev = { viewModel.prevSearchResult() },
                    onClose = { viewModel.closeSearch() }
                )
            }

            // Read Aloud Floating Controls (TTS)
            AnimatedVisibility(
                visible = isTtsActive,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReadAloudBar(
                    isPlaying = isTtsPlaying,
                    currentPage = uiState.currentPage,
                    totalPages = totalPages,
                    currentSpeed = ttsSpeed,
                    onTogglePlayPause = {
                        if (isTtsPlaying) readAloudService?.pause() else readAloudService?.resume()
                    },
                    onNextPage = { readAloudService?.nextPage() },
                    onPrevPage = { readAloudService?.prevPage() },
                    onSelectSpeed = { speed -> readAloudService?.setSpeed(speed) },
                    onClose = {
                        readAloudService?.stopPlayback()
                        isTtsActive = false
                    }
                )
            }

            // Minimalist Floating Page Indicator when controls are hidden
            AnimatedVisibility(
                visible = !uiState.isControlsVisible && !isTtsActive && totalPages > 0,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = "Page ${uiState.currentPage + 1} of $totalPages",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    // Eye Rest Break Reminder Dialog
    if (uiState.breakReminderMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissBreakReminder() },
            title = { Text("Eye Rest Reminder") },
            text = { Text(uiState.breakReminderMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissBreakReminder() }) {
                    Text("Got it")
                }
            }
        )
    }

    // Display Settings Sheet
    if (showDisplaySettings) {
        DisplaySettingsSheet(
            readingMode = uiState.readingMode,
            appTheme = appSettings.themeMode,
            scrollMode = uiState.scrollMode,
            warmFilterStrength = uiState.warmFilterStrength,
            brightness = uiState.brightness,
            autoCropMargins = uiState.autoCropMargins,
            onSelectReadingMode = { viewModel.setReadingMode(it) },
            onSelectAppTheme = { viewModel.setAppTheme(it) },
            onSelectScrollMode = { viewModel.setScrollMode(it) },
            onWarmFilterChange = { viewModel.setWarmFilterStrength(it) },
            onBrightnessChange = { viewModel.setBrightness(it) },
            onToggleAutoCrop = { viewModel.toggleAutoCrop() },
            onDismiss = { showDisplaySettings = false }
        )
    }

    // Table of Contents Sheet
    if (showToc) {
        TableOfContentsSheet(
            chapters = uiState.chapters,
            totalPages = totalPages,
            currentPage = uiState.currentPage,
            isLoading = uiState.isExtractingChapters,
            onSelectChapter = { page -> viewModel.jumpToPage(page, recordHistory = true) },
            onDismiss = { showToc = false }
        )
    }

    // Bookmarks & Notes Sheet
    if (showBookmarks) {
        BookmarksNotesSheet(
            bookmarks = uiState.bookmarks,
            highlights = uiState.highlights,
            onSelectPage = { page -> viewModel.jumpToPage(page, recordHistory = true) },
            onDeleteBookmark = { id -> viewModel.deleteBookmark(id) },
            onDeleteHighlight = { id -> viewModel.deleteHighlight(id) },
            onExportNotes = { viewModel.getExportNotesText() },
            onDismiss = { showBookmarks = false }
        )
    }

    // Go to Page Dialog
    if (showGoToPageDialog) {
        GoToPageDialog(
            currentPage = uiState.currentPage,
            totalPages = totalPages,
            onDismiss = { showGoToPageDialog = false },
            onPageSelected = { page ->
                viewModel.jumpToPage(page, recordHistory = true)
            }
        )
    }
}
