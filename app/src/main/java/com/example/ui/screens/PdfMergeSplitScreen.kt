package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.PdfViewModel
import java.io.File
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfMergeSplitScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var activeSubTool by remember { mutableStateOf("merge") } // merge or split
    val progress by viewModel.progressStatus.collectAsState()

    // 1. Merge States
    val filesToMerge by viewModel.filesToMerge.collectAsState()
    var outputMergeTitle by remember { mutableStateOf("Merged_Documents_Output") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                // Copy selected file stream to directory so we can merge it
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val file = File(context.cacheDir, "input_${System.currentTimeMillis()}.pdf")
                    file.outputStream().use { stream.copyTo(it) }
                    viewModel.queueToMerge(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 2. Split States
    var splitCountInput by remember { mutableStateOf("1") }
    val currentDocPath by viewModel.currentFilePath.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merge & Split Pages", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                        Text(progress ?: "Processing..", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // Toolbar Selector
            TabRow(
                selectedTabIndex = if (activeSubTool == "merge") 0 else 1
            ) {
                Tab(
                    selected = activeSubTool == "merge",
                    onClick = { activeSubTool = "merge" },
                    text = { Text("Merge Multiple PDFs") }
                )
                Tab(
                    selected = activeSubTool == "split",
                    onClick = { activeSubTool = "split" },
                    text = { Text("Split Document Pages") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (activeSubTool == "merge") {
                    // Merge Section
                    Text(
                        "Add multiple PDF documents, sort their order, and merge them.",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = outputMergeTitle,
                        onValueChange = { outputMergeTitle = it },
                        label = { Text("OutputFile Name") },
                        modifier = Modifier.fillMaxWidth().testTag("merge_pdf_output_name")
                    )

                    Button(
                        onClick = { filePickerLauncher.launch("application/pdf") },
                        modifier = Modifier.fillMaxWidth().testTag("select_pdf_to_merge_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add PDF File to Compilation")
                    }

                    // Selected files listing
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1F)
                            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2F)),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filesToMerge) { index, file ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1F)) {
                                        Text(
                                            "File ${index + 1}: ${file.nameWithoutExtension.take(24)}",
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { viewModel.swapMergeFiles(index, index - 1) },
                                            enabled = index > 0
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Up")
                                        }
                                        IconButton(
                                            onClick = { viewModel.swapMergeFiles(index, index + 1) },
                                            enabled = index < filesToMerge.size - 1
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Down")
                                        }
                                        IconButton(onClick = { viewModel.dequeueFromMerge(file) }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Remove")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearMergeQueue() },
                            modifier = Modifier.weight(1F)
                        ) {
                            Text("Clear Queue")
                        }

                        Button(
                            onClick = {
                                viewModel.executeMerge(outputMergeTitle) {
                                    onNavigateBack()
                                }
                            },
                            enabled = filesToMerge.size >= 2,
                            modifier = Modifier.weight(1.5F).testTag("compile_merge_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Merge PDFs")
                        }
                    }
                } else {
                    // Split Section
                    Text(
                        "Split the currently active document by page intervals",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    if (currentDocPath == null) {
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
                                Text("Please open or pick a PDF document first to make splits available.")
                            }
                        }
                        Spacer(modifier = Modifier.weight(1F))
                    } else {
                        val activeFile = File(currentDocPath!!)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null)
                                Column {
                                    Text("Active PDF file:", fontWeight = FontWeight.Bold)
                                    Text(activeFile.name)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = splitCountInput,
                            onValueChange = { splitCountInput = it },
                            label = { Text("Split every (N) pages") },
                            modifier = Modifier.fillMaxWidth().testTag("split_count_input"),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )

                        Spacer(modifier = Modifier.weight(1F))

                        Button(
                            onClick = {
                                val size = splitCountInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                viewModel.executeSplit(size) {
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("compile_split_btn")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Split Document Now")
                        }
                    }
                }
            }
        }
    }
}
