package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.PdfViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val currentPath by viewModel.currentFilePath.collectAsState()
    val pageCount by viewModel.pageCount.collectAsState()
    val currentPageIdx by viewModel.currentPageIndex.collectAsState()
    val pageBitmaps by viewModel.pageBitmaps.collectAsState()
    val thumbnailBitmaps by viewModel.thumbnailBitmaps.collectAsState()
    val vibe by viewModel.vibeTheme.collectAsState()
    val isSidebarOpen by viewModel.isThumbnailSidebarOpen.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    // Editor annotation setup
    var isDrawingMode by remember { mutableStateOf(false) }
    var drawingColor by remember { mutableStateOf(Color.Red) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Page Jump dialog state
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInputText by remember { mutableStateOf("") }

    // Page-specific annotations maps to preserve individual drawings and stamp positions
    val allPagesPaths = remember { mutableStateMapOf<Int, List<Pair<List<Offset>, Color>>>() }
    val userPaths = allPagesPaths[currentPageIdx] ?: emptyList()
    val currentPathPoints = remember { mutableStateListOf<Offset>() }
    val pageStamps = remember { mutableStateMapOf<Int, String?>() }
    val activeStamp = pageStamps[currentPageIdx]

    // Reset zoom and offsets on page navigation
    LaunchedEffect(currentPageIdx) {
        scale = 1f
        offset = Offset.Zero
    }

    val fileTitle = currentPath?.let { File(it).name } ?: "PDF Document"
    val isBookmarked = bookmarks.any { it.pageNumber == currentPageIdx }

    // Color Inversion ColorMatrices for Dark, Sepia, and Night modes of rendered pages
    val activeColorFilter = when (vibe) {
        PdfViewModel.ReaderVibe.DARK -> ColorFilter.colorMatrix(
            ColorMatrix(floatArrayOf(
                -1f,  0f,  0f, 0f, 255f,
                 0f, -1f,  0f, 0f, 255f,
                 0f,  0f, -1f, 0f, 255f,
                 0f,  0f,  0f, 1f,   0f
            ))
        )
        PdfViewModel.ReaderVibe.SEPIA -> ColorFilter.colorMatrix(
            ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f
            ))
        )
        PdfViewModel.ReaderVibe.NIGHT -> ColorFilter.tint(
            Color(0xFF112233),
            BlendMode.Multiply
        )
        else -> null
    }

    val backgroundBrush = when (vibe) {
        PdfViewModel.ReaderVibe.DARK -> SolidColor(Color(0xFF1E1E1E))
        PdfViewModel.ReaderVibe.SEPIA -> SolidColor(Color(0xFFF4ECD8))
        PdfViewModel.ReaderVibe.NIGHT -> SolidColor(Color(0xFF0F172A))
        else -> SolidColor(Color(0xFFF1F5F9))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable {
                            if (pageCount > 1) {
                                jumpInputText = "${currentPageIdx + 1}"
                                showJumpDialog = true
                            }
                        }
                    ) {
                        Text(
                            fileTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Text(
                            if (pageCount > 0) "Page ${currentPageIdx + 1} of $pageCount • Tap to Jump" else "Loading...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return")
                    }
                },
                actions = {
                    // Share Document action
                    IconButton(
                        onClick = {
                            currentPath?.let { path ->
                                viewModel.shareDocument(context, path)
                            }
                        },
                        modifier = Modifier.testTag("share_pdf_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share PDF Document")
                    }
                    IconButton(
                        onClick = { viewModel.togglePageBookmark(currentPageIdx) },
                        modifier = Modifier.testTag("bookmark_toggle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Toggle page bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleSidebar() },
                        modifier = Modifier.testTag("sidebar_toggle_btn")
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Sidebar thumbnails")
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Formatting Controls Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zoom In
                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceAtMost(3.5f) },
                        modifier = Modifier.testTag("zoom_in_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Zoom In")
                    }
                    // Reset Zoom
                    IconButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        modifier = Modifier.testTag("reset_zoom_btn")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset Zoom")
                    }
                    // Zoom Out
                    IconButton(
                        onClick = { scale = (scale - 0.25f).coerceAtLeast(0.6f) },
                        modifier = Modifier.testTag("zoom_out_btn")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Zoom Out")
                    }

                    VerticalDivider(modifier = Modifier.height(24.dp))

                    // Freehand markup stylus
                    IconButton(
                        onClick = { isDrawingMode = !isDrawingMode },
                        modifier = Modifier.testTag("drawing_mode_btn"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isDrawingMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Filled.Create, contentDescription = "Stylus Drawing")
                    }

                    // Approved stamp cycle (Cycle: CONFIDENTIAL, APPROVED, REVIEWED, DRAFT, null)
                    IconButton(
                        onClick = {
                            val nextStamp = when (activeStamp) {
                                null -> "CONFIDENTIAL"
                                "CONFIDENTIAL" -> "APPROVED"
                                "APPROVED" -> "REVIEWED"
                                "REVIEWED" -> "DRAFT"
                                "DRAFT" -> null
                                else -> null
                            }
                            pageStamps[currentPageIdx] = nextStamp
                        },
                        modifier = Modifier.testTag("stamp_cycle_btn"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (activeStamp != null) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = "Approval Stamps")
                    }
                }

                // Sub-controls for drawing elements (Color Picker & Clear option)
                AnimatedVisibility(visible = isDrawingMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ink Color: ", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            listOf(Color.Red, Color.Blue, Color(0xFFEAB308), Color(0xFF22C55E)).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(color, RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (drawingColor == color) 2.dp else 1.dp,
                                            color = if (drawingColor == color) MaterialTheme.colorScheme.primary else Color.LightGray,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { drawingColor = color }
                                )
                            }
                        }

                        TextButton(
                            onClick = { allPagesPaths[currentPageIdx] = emptyList() },
                            enabled = userPaths.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear ink", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Ink", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Sub-controls for stamps
                AnimatedVisibility(visible = activeStamp != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Active Page Stamp: ", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = activeStamp ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.Red)
                            )
                        }

                        TextButton(onClick = { pageStamps[currentPageIdx] = null }) {
                            Text("Remove Stamp", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Core Reader Theme Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VibeThemeButton(
                        modifier = Modifier.weight(1F),
                        text = "Light",
                        active = vibe == PdfViewModel.ReaderVibe.LIGHT,
                        onClick = { viewModel.setVibeTheme(PdfViewModel.ReaderVibe.LIGHT) }
                    )
                    VibeThemeButton(
                        modifier = Modifier.weight(1F),
                        text = "Dark",
                        active = vibe == PdfViewModel.ReaderVibe.DARK,
                        onClick = { viewModel.setVibeTheme(PdfViewModel.ReaderVibe.DARK) }
                    )
                    VibeThemeButton(
                        modifier = Modifier.weight(1F),
                        text = "Sepia",
                        active = vibe == PdfViewModel.ReaderVibe.SEPIA,
                        onClick = { viewModel.setVibeTheme(PdfViewModel.ReaderVibe.SEPIA) }
                    )
                    VibeThemeButton(
                        modifier = Modifier.weight(1F),
                        text = "Night",
                        active = vibe == PdfViewModel.ReaderVibe.NIGHT,
                        onClick = { viewModel.setVibeTheme(PdfViewModel.ReaderVibe.NIGHT) }
                    )
                }

                // Page Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.seekToPage(currentPageIdx - 1) },
                        enabled = currentPageIdx > 0,
                        modifier = Modifier.testTag("prev_page_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prev")
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            if (pageCount > 1) {
                                jumpInputText = "${currentPageIdx + 1}"
                                showJumpDialog = true
                            }
                        }
                    ) {
                        Text(
                            text = if (pageCount > 0) "${currentPageIdx + 1} / $pageCount" else "0 / 0",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Button(
                        onClick = { viewModel.seekToPage(currentPageIdx + 1) },
                        enabled = currentPageIdx < pageCount - 1,
                        modifier = Modifier.testTag("next_page_btn")
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(backgroundBrush)
        ) {
            // Document View Area
            Box(
                modifier = Modifier
                    .weight(1F)
                    .fillMaxHeight()
                    .pointerInput(isDrawingMode) {
                        if (isDrawingMode) return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.6f, 3.5f)
                            offset = offset + pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val bitmapToPage = pageBitmaps[currentPageIdx]

                if (bitmapToPage != null) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .border(1.dp, Color.LightGray.copy(alpha = 0.5f))
                    ) {
                        // Injected Android page canvas bitmap
                        Image(
                            painter = BitmapPainter(bitmapToPage.asImageBitmap()),
                            contentDescription = "Active page raw canvas render",
                            colorFilter = activeColorFilter,
                            modifier = Modifier.fillMaxWidth(0.95f)
                        )

                        // Freehand Drawing Canvas Overlay
                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(isDrawingMode, drawingColor) {
                                    if (!isDrawingMode) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { startOffset ->
                                            currentPathPoints.clear()
                                            currentPathPoints.add(startOffset)
                                        },
                                        onDragEnd = {
                                            val currentPathsList = allPagesPaths[currentPageIdx]?.toMutableList() ?: mutableListOf()
                                            currentPathsList.add(Pair(currentPathPoints.toList(), drawingColor))
                                            allPagesPaths[currentPageIdx] = currentPathsList
                                            currentPathPoints.clear()
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentPathPoints.add(change.position)
                                        }
                                    )
                                }
                        ) {
                            userPaths.forEach { (pointsList, inkColor) ->
                                if (pointsList.size > 1) {
                                    val path = Path().apply {
                                        moveTo(pointsList.first().x, pointsList.first().y)
                                        for (i in 1 until pointsList.size) {
                                            lineTo(pointsList[i].x, pointsList[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = inkColor,
                                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }

                            if (currentPathPoints.size > 1) {
                                val currentPathObj = Path().apply {
                                    moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                                    for (i in 1 until currentPathPoints.size) {
                                        lineTo(currentPathPoints[i].x, currentPathPoints[i].y)
                                    }
                                }
                                drawPath(
                                    path = currentPathObj,
                                    color = drawingColor,
                                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }

                        // APPROVED / CONFIDENTIAL watermark overlay stamp
                        if (activeStamp != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(20.dp)
                                    .border(3.dp, Color.Red, RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.85F))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = activeStamp,
                                    color = Color.Red,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Rendering page canvas...",
                            color = if (vibe == PdfViewModel.ReaderVibe.LIGHT) Color.Black else Color.White
                        )
                    }
                }
            }

            // Thumbnail list sidebar
            AnimatedVisibility(
                visible = isSidebarOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it })
            ) {
                Card(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .border(1.dp, Color.LightGray.copy(alpha = 0.3F)),
                    shape = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pageCount) { idx ->
                            val thumbnailBitmap = thumbnailBitmaps[idx]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { viewModel.seekToPage(idx) }
                                    .padding(4.dp)
                            ) {
                                if (thumbnailBitmap != null) {
                                    Image(
                                        painter = BitmapPainter(thumbnailBitmap.asImageBitmap()),
                                        contentDescription = "thumbnail",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .border(
                                                width = if (idx == currentPageIdx) 2.dp else 1.dp,
                                                color = if (idx == currentPageIdx) MaterialTheme.colorScheme.primary else Color.Gray,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .background(Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(strokeWidth = 1.dp, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text("p. ${idx + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column {
                    Text("Enter page number (1 - $pageCount):", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jumpInputText,
                        onValueChange = { jumpInputText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetPage = jumpInputText.toIntOrNull()
                        if (targetPage != null && targetPage in 1..pageCount) {
                            viewModel.seekToPage(targetPage - 1)
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun VibeThemeButton(
    modifier: Modifier = Modifier,
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier.height(36.dp),
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
