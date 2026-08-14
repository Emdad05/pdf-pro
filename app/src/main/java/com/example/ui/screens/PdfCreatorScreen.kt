package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.pdf.PdfEngine
import com.example.ui.viewmodel.PdfViewModel
import java.io.File
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfCreatorScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    initialTab: String = "images"
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(initialTab) }
    val progress by viewModel.progressStatus.collectAsState()

    // 1. Image Mode States
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var outputPdfTitle by remember { mutableStateOf("My_Images_Compilation") }
    var fitMode by remember { mutableStateOf(PdfEngine.ImageFitMode.FIT) }

    // Multi Image Picker Launcher
    val imagesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        selectedImageUris = uris
    }

    // 2. Text Mode States
    var formattedText by remember { mutableStateOf("") }
    var textDocumentTitle by remember { mutableStateOf("Report_File") }
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }

    // 3. Web HTML / URL States
    var webSourceUrl by remember { mutableStateOf("https://news.ycombinator.com") }
    var pastedHtml by remember { mutableStateOf("<h1>My Custom HTML Document</h1><p>Processed completely offline inside Android.</p>") }
    var isWebUrlMode by remember { mutableStateOf(true) }
    var webOutputTitle by remember { mutableStateOf("HTML_Page_Layout") }

    // 4. DOCX States
    var selectedDocxFile by remember { mutableStateOf<File?>(null) }
    val docxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                // Copy stream to cache file to parse docx
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val tempDocx = File(context.cacheDir, "input_doc.docx")
                    tempDocx.outputStream().use { stream.copyTo(it) }
                    selectedDocxFile = tempDocx
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compile PDF Sources", fontWeight = FontWeight.Bold) },
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
            // Processing status banner
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
                        Text(progress ?: "Compiling..", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // Tabs Selector
            TabRow(
                selectedTabIndex = when (selectedTab) {
                    "images" -> 0
                    "text" -> 1
                    "web" -> 2
                    "docx" -> 3
                    else -> 0
                }
            ) {
                Tab(
                    selected = selectedTab == "images",
                    onClick = { selectedTab = "images" },
                    text = { Text("Images") }
                )
                Tab(
                    selected = selectedTab == "text",
                    onClick = { selectedTab = "text" },
                    text = { Text("Rich Text") }
                )
                Tab(
                    selected = selectedTab == "web",
                    onClick = { selectedTab = "web" },
                    text = { Text("HTML/Web") }
                )
                Tab(
                    selected = selectedTab == "docx",
                    onClick = { selectedTab = "docx" },
                    text = { Text("Word DOCX") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    "images" -> {
                        // 1. From Images Pane
                        Text(
                            "Convert single/multiple pictures to standard PDF",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        OutlinedTextField(
                            value = outputPdfTitle,
                            onValueChange = { outputPdfTitle = it },
                            label = { Text("Output Document Title") },
                            modifier = Modifier.fillMaxWidth().testTag("image_pdf_title")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Page Fit Mode:")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = fitMode == PdfEngine.ImageFitMode.FIT,
                                    onClick = { fitMode = PdfEngine.ImageFitMode.FIT },
                                    label = { Text("Aspect Fit") }
                                )
                                FilterChip(
                                    selected = fitMode == PdfEngine.ImageFitMode.FILL,
                                    onClick = { fitMode = PdfEngine.ImageFitMode.FILL },
                                    label = { Text("Fill Screen") }
                                )
                            }
                        }

                        Button(
                            onClick = { imagesPicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth().testTag("add_photos_btn")
                        ) {
                            Icon(Icons.Filled.AddCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Images (${selectedImageUris.size} chosen)")
                        }

                        // Preview Grid list of selected items
                        if (selectedImageUris.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3F), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(selectedImageUris) { uri ->
                                    Box(
                                        modifier = Modifier
                                            .width(90.dp)
                                            .fillMaxHeight()
                                            .background(Color.White)
                                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                            .clip(RoundedCornerShape(4.dp))
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1F))

                        Button(
                            onClick = {
                                viewModel.executeImagesToPdfConversion(selectedImageUris, outputPdfTitle, fitMode) {
                                    onNavigateBack()
                                }
                            },
                            enabled = selectedImageUris.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().testTag("compile_images_btn")
                        ) {
                            Icon(Icons.Filled.Build, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compile to PDF")
                        }
                    }

                    "text" -> {
                        // 2. From Text Pane
                        Text(
                            "Generate Multi-page PDF from formatted Rich text",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        OutlinedTextField(
                            value = textDocumentTitle,
                            onValueChange = { textDocumentTitle = it },
                            label = { Text("Document Header Label") },
                            modifier = Modifier.fillMaxWidth().testTag("text_pdf_title")
                        )

                         Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconToggleButton(
                                checked = isBold,
                                onCheckedChange = { isBold = it },
                                colors = IconButtonDefaults.iconToggleButtonColors(
                                    containerColor = if (isBold) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = "Bold style")
                            }

                            IconToggleButton(
                                checked = isItalic,
                                onCheckedChange = { isItalic = it },
                                colors = IconButtonDefaults.iconToggleButtonColors(
                                    containerColor = if (isItalic) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Italic style")
                            }
                        }

                        OutlinedTextField(
                            value = formattedText,
                            onValueChange = { formattedText = it },
                            label = { Text("Write your text summary or page notes here..") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1F),
                            maxLines = 15
                        )

                        Button(
                            onClick = {
                                viewModel.executeTextToPdfConversion(formattedText, textDocumentTitle) {
                                    onNavigateBack()
                                }
                            },
                            enabled = formattedText.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().testTag("compile_text_btn")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Text Document")
                        }
                    }

                    "web" -> {
                        // 3. From HTML/Web Pane
                        Text(
                            "Compile direct Web URL layouts or raw HTML blocks",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        OutlinedTextField(
                            value = webOutputTitle,
                            onValueChange = { webOutputTitle = it },
                            label = { Text("File Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = isWebUrlMode, onClick = { isWebUrlMode = true })
                                Text("Web URL")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !isWebUrlMode, onClick = { isWebUrlMode = false })
                                Text("HTML Markup")
                            }
                        }

                        if (isWebUrlMode) {
                            OutlinedTextField(
                                value = webSourceUrl,
                                onValueChange = { webSourceUrl = it },
                                label = { Text("Destination URL Link") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = pastedHtml,
                                onValueChange = { pastedHtml = it },
                                label = { Text("Raw HTML Elements String") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1F)
                            )
                        }

                        if (isWebUrlMode) {
                            Spacer(modifier = Modifier.weight(1F))
                        }

                        Button(
                            onClick = {
                                val source = if (isWebUrlMode) webSourceUrl else pastedHtml
                                viewModel.executeWebUrlToPdfConversion(source, isWebUrlMode, webOutputTitle) {
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("compile_html_btn")
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Render HTML to PDF")
                        }
                    }

                    "docx" -> {
                        // 4. From Word DOCX Pane
                        Text(
                            "Convert structured Word Document files (.docx) to PDF",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5F))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5F)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (selectedDocxFile != null) {
                                    Text(
                                        "Selected Word File: ${selectedDocxFile!!.name}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text("No Word file selected")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { docxLauncher.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document") }) {
                                    Text("Pick DOCX Document")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1F))

                        Button(
                            onClick = {
                                selectedDocxFile?.let { file ->
                                    viewModel.executeDocxToPdfConversion(file) {
                                        onNavigateBack()
                                    }
                                }
                            },
                            enabled = selectedDocxFile != null,
                            modifier = Modifier.fillMaxWidth().testTag("compile_docx_btn")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Parse & Compile DOCX")
                        }
                    }
                }
            }
        }
    }
}
