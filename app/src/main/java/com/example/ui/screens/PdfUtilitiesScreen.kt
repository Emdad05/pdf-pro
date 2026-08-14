package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.PdfViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfUtilitiesScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit
) {
    var activeToolSub by remember { mutableStateOf("compress") } // compress, rotate, watermark, ocr
    val progress by viewModel.progressStatus.collectAsState()
    val currentFilePath by viewModel.currentFilePath.collectAsState()

    // 1. Compression States
    var qualityRatioSlider by remember { mutableStateOf(0.7f) }

    // 2. Rotation States
    var selectedDegrees by remember { mutableStateOf(90f) }

    // 3. Watermark States
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var addPageNumbers by remember { mutableStateOf(true) }
    var headerLabel by remember { mutableStateOf("") }
    var footerLabel by remember { mutableStateOf("Internal Use Only") }

    // 4. OCR / Text / Structure Inspector State
    var textOutputPreview by remember { mutableStateOf("") }

    LaunchedEffect(currentFilePath, activeToolSub) {
        if (activeToolSub == "ocr" && currentFilePath != null) {
            textOutputPreview = viewModel.inspectCurrentDocument()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Engine Utilities", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("utilities_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Live action indicator
            AnimatedVisibility(visible = progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(progress ?: "Running utility...", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // Toolbar Selector
            ScrollableTabRow(
                selectedTabIndex = when (activeToolSub) {
                    "compress" -> 0
                    "rotate" -> 1
                    "watermark" -> 2
                    "ocr" -> 3
                    else -> 0
                }
            ) {
                Tab(
                    selected = activeToolSub == "compress",
                    onClick = { activeToolSub = "compress" },
                    text = { Text("Compress Size") }
                )
                Tab(
                    selected = activeToolSub == "rotate",
                    onClick = { activeToolSub = "rotate" },
                    text = { Text("Rotate Pages") }
                )
                Tab(
                    selected = activeToolSub == "watermark",
                    onClick = { activeToolSub = "watermark" },
                    text = { Text("Watermark / Headers") }
                )
                Tab(
                    selected = activeToolSub == "ocr",
                    onClick = { activeToolSub = "ocr" },
                    text = { Text("Inspect & Structure") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentFilePath == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text("Please load or select a PDF document first to make workspace tools accessible.")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1F))
                } else {
                    val fileObj = File(currentFilePath!!)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null)
                            Column {
                                Text("Active File:", fontWeight = FontWeight.Bold)
                                Text(fileObj.name)
                            }
                        }
                    }

                    when (activeToolSub) {
                        "compress" -> {
                            Text(
                                "Reduce PDF File Size via Matrix Compression",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text("Adjust the slider to scale down page rendering dimensions and compress image streams:")

                            Spacer(modifier = Modifier.height(10.dp))

                            Slider(
                                value = qualityRatioSlider,
                                onValueChange = { qualityRatioSlider = it },
                                valueRange = 0.2f..0.9f,
                                modifier = Modifier.fillMaxWidth().testTag("compress_slider")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("High Compression (Smaller File)")
                                Text("Lower Compression (Higher Detail)")
                            }

                            Text(
                                "Configured scale ratio: ${(qualityRatioSlider * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.weight(1F))

                            Button(
                                onClick = {
                                viewModel.executeCompression(qualityRatioSlider) {
                                        onNavigateBack()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("compile_compress_btn")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compress PDF File Size Now")
                            }
                        }

                        "rotate" -> {
                            Text(
                                "Rotate Document Pages Natively",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                FilterChip(
                                    selected = selectedDegrees == 90f,
                                    onClick = { selectedDegrees = 90f },
                                    label = { Text("Rotate 90°") }
                                )
                                FilterChip(
                                    selected = selectedDegrees == 180f,
                                    onClick = { selectedDegrees = 180f },
                                    label = { Text("Rotate 180°") }
                                )
                                FilterChip(
                                    selected = selectedDegrees == 270f,
                                    onClick = { selectedDegrees = 270f },
                                    label = { Text("Rotate 270°") }
                                )
                            }

                            Spacer(modifier = Modifier.weight(1F))

                            Button(
                                onClick = {
                                    viewModel.executeRotation(selectedDegrees) {
                                        onNavigateBack()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("compile_rotate_btn")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Apply Rotate Matrices")
                            }
                        }

                        "watermark" -> {
                            Text(
                                "Burn Watermarks, Headers & Page Numbers",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                label = { Text("Center Watermark Text") },
                                modifier = Modifier.fillMaxWidth().testTag("watermark_text_input")
                            )

                            OutlinedTextField(
                                value = headerLabel,
                                onValueChange = { headerLabel = it },
                                label = { Text("Header Text (Top Left)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = footerLabel,
                                onValueChange = { footerLabel = it },
                                label = { Text("Footer Text (Bottom Right)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Embed Automatic Page Numbers (Center Bottom)")
                                Switch(
                                    checked = addPageNumbers,
                                    onCheckedChange = { addPageNumbers = it },
                                    modifier = Modifier.testTag("page_nums_switch")
                                )
                            }

                            Spacer(modifier = Modifier.weight(1F))

                            Button(
                                onClick = {
                                    viewModel.executeWatermark(
                                        watermarkText = watermarkText,
                                        watermarkColor = 0x30808080,
                                        addPageNums = addPageNumbers,
                                        headerText = headerLabel,
                                        footerText = footerLabel
                                    ) {
                                        onNavigateBack()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("compile_watermark_btn")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stamp & Hot-burn Elements")
                            }
                        }

                        "ocr" -> {
                            Text(
                                "Native PDF Document Inspector",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1F),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = if (textOutputPreview.isNotEmpty()) textOutputPreview else "Inspecting PDF document...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
