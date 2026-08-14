package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AnnotationEntity
import com.example.data.database.BookmarkEntity
import com.example.data.database.RecentDocument
import com.example.data.pdf.PdfEngine
import com.example.data.repository.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PdfRepository = (application as com.example.PdfApplication).repository
    private val rendererLock = Any()

    // Recent Documents from database
    val recentDocuments: StateFlow<List<RecentDocument>> = repository.recentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active document states
    private val _currentFilePath = MutableStateFlow<String?>(null)
    val currentFilePath: StateFlow<String?> = _currentFilePath.asStateFlow()

    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _isDoublePageSpread = MutableStateFlow(false)
    val isDoublePageSpread: StateFlow<Boolean> = _isDoublePageSpread.asStateFlow()

    private val _vibeTheme = MutableStateFlow(ReaderVibe.LIGHT)
    val vibeTheme: StateFlow<ReaderVibe> = _vibeTheme.asStateFlow()

    private val _isThumbnailSidebarOpen = MutableStateFlow(false)
    val isThumbnailSidebarOpen: StateFlow<Boolean> = _isThumbnailSidebarOpen.asStateFlow()

    // Page-cached bitmaps to load fast
    private val _pageBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pageBitmaps: StateFlow<Map<Int, Bitmap>> = _pageBitmaps.asStateFlow()

    // Dedicated sidebar thumbnails cache
    private val _thumbnailBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val thumbnailBitmaps: StateFlow<Map<Int, Bitmap>> = _thumbnailBitmaps.asStateFlow()

    // Current Document Bookmarks
    val bookmarks: StateFlow<List<BookmarkEntity>> = _currentFilePath
        .flatMapLatest { path ->
            if (path != null) repository.getBookmarksForDocument(path) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current Document Annotations
    val annotations: StateFlow<List<AnnotationEntity>> = _currentFilePath
        .flatMapLatest { path ->
            if (path != null) repository.getAnnotationsForDocument(path) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Multi-select list for Merging
    private val _filesToMerge = MutableStateFlow<List<File>>(emptyList())
    val filesToMerge: StateFlow<List<File>> = _filesToMerge.asStateFlow()

    // Processing statuses
    private val _progressStatus = MutableStateFlow<String?>(null)
    val progressStatus: StateFlow<String?> = _progressStatus.asStateFlow()

    private var currentPdfRenderer: PdfRenderer? = null
    private var currentPfd: ParcelFileDescriptor? = null

    enum class ReaderVibe { LIGHT, DARK, SEPIA, NIGHT }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            preheatSampleDocuments()
        }
    }

    private suspend fun preheatSampleDocuments() {
        val filesDir = getApplication<Application>().filesDir
        val sample1 = File(filesDir, "Introduction_to_PDF_Tools.pdf")
        if (!sample1.exists()) {
            val welcomeText = """
                PDF Tools Suite - Welcome Guide
                ==================================
                
                This utility application represents a modern, offline-first production workspace for PDF viewing, compilation, conversion, and surgical page-level manipulations, compiled and signed inside a hardened client architecture.
                
                Features Supported Natively:
                ----------------------------------
                1. Reader: Light/Dark Page-Inversion, Warm Sepia filter, Page Thumbnail Sidebars, Double Page spreads, and custom Bookmarks.
                2. Editor: Page rotate-and-crop, watermark burning, page numbering stamps, and custom drawing stylus annotations.
                3. Conversions: Full layout-aware DOCX parsing, multi-page Rich Text rendering, Web HTML compilation, and sheet CSV exports.
                4. Merges & Splits: Document re-ordering, interval splits, and multi-file batch compiles.
                
                Security & Privacy Compliance:
                ----------------------------------
                Built strictly with local Android rendering pipelines, guaranteeing that all documents remain completely on your local device.
                
                Secure your records, optimize your workflow. Enjoy full-featured offline PDF manipulation.
            """.trimIndent()
            PdfEngine.convertTextToPdf(welcomeText, sample1, PdfEngine.PAGE_SIZE_A4, margins = 54, titleText = "PDF Tools Workspace Guide")
        }

        val sample2 = File(filesDir, "Monthly_Receipt_Log.pdf")
        if (!sample2.exists()) {
            val csvData = """
                ID, Category, Description, Amount
                TX-1090, Software Subscription, Google Cloud Platform, $45.00
                TX-1091, Infrastructure, Dedicated Server Cluster, $1,540.23
                TX-1092, Assessment, Security & Compliance Audit, $12,500.00
                TX-1093, Workspace, Hardware Security Keys, $250.00
                TX-1094, Network, Private Fiber Relay, $380.00
            """.trimIndent()
            val tempCsv = File(filesDir, "temp.csv")
            tempCsv.writeText(csvData)
            PdfEngine.convertExcelToPdf(tempCsv, sample2, PdfEngine.PAGE_SIZE_A4)
            tempCsv.delete()
        }

        val sample3 = File(filesDir, "Product_Release_Notes.pdf")
        if (!sample3.exists()) {
            val noteText = """
                Surgical Compilation Pipeline Changelog
                ----------------------------------------
                - Native PDF page rendering with color inversion filters.
                - Color matrices for Dark, Sepia, and Night view modes.
                - Multi-page DOCX XML parser and rich text compiler.
                - Page-level rotation, watermarking, and compression tools.
                - Local structural metadata inspector.
            """.trimIndent()
            PdfEngine.convertTextToPdf(noteText, sample3, PdfEngine.PAGE_SIZE_LETTER, margins = 60, titleText = "Engine Version 1.0.0")
        }

        // Add to recents so workspace shows them on launch
        repository.addRecentDocument(RecentDocument(sample1.absolutePath, "Product Workspace Guide.pdf", 0, 2))
        repository.addRecentDocument(RecentDocument(sample2.absolutePath, "Staging Cost Sheet.pdf", 0, 1))
        repository.addRecentDocument(RecentDocument(sample3.absolutePath, "Engine Changelog.pdf", 0, 1))
    }

    /**
     * Loads a PDF file and opens the local PdfRenderer
     */
    fun loadDocument(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@launch

                synchronized(rendererLock) {
                    closeCurrentRendererInternal()

                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    currentPfd = pfd
                    currentPdfRenderer = renderer

                    _currentFilePath.value = filePath
                    _pageCount.value = renderer.pageCount
                    _currentPageIndex.value = 0
                    _pageBitmaps.value = emptyMap()
                    _thumbnailBitmaps.value = emptyMap()
                }

                // Add to persistent recent documents
                val count = _pageCount.value
                repository.addRecentDocument(
                    RecentDocument(
                        filePath = filePath,
                        title = file.name,
                        lastPageRead = 0,
                        totalPages = count
                    )
                )

                // Preheat initial pages & thumbnails
                preheatPageCache(0)
                preheatThumbnails()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeRecent(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeRecentDocument(filePath)
        }
    }

    fun seekToPage(index: Int) {
        val total = _pageCount.value
        if (total > 0 && index in 0 until total) {
            _currentPageIndex.value = index
            preheatPageCache(index)

            val path = _currentFilePath.value
            if (path != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val file = File(path)
                    repository.addRecentDocument(
                        RecentDocument(
                            filePath = path,
                            title = file.name,
                            lastPageRead = index,
                            totalPages = total
                        )
                    )
                }
            }
        }
    }

    private fun preheatPageCache(centerIndex: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val updatedBitmaps = _pageBitmaps.value.toMutableMap()
            val total = _pageCount.value
            if (total <= 0) return@launch

            val indicesToCache = listOf(centerIndex - 1, centerIndex, centerIndex + 1, centerIndex + 2)
                .filter { it in 0 until total }

            // Trim keys far from view window to keep memory footprint light
            val keysToRemove = updatedBitmaps.keys.filter { Math.abs(it - centerIndex) > 3 }
            keysToRemove.forEach { updatedBitmaps.remove(it) }

            indicesToCache.forEach { idx ->
                if (!updatedBitmaps.containsKey(idx)) {
                    synchronized(rendererLock) {
                        val renderer = currentPdfRenderer
                        if (renderer != null && idx < renderer.pageCount) {
                            try {
                                val originalPage = renderer.openPage(idx)
                                try {
                                    val scaleFactor = 2.0f
                                    val w = (originalPage.width * scaleFactor).toInt().coerceAtLeast(100)
                                    val h = (originalPage.height * scaleFactor).toInt().coerceAtLeast(100)
                                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    val matrix = android.graphics.Matrix()
                                    matrix.setScale(scaleFactor, scaleFactor)
                                    originalPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    updatedBitmaps[idx] = bitmap
                                } finally {
                                    originalPage.close()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
            _pageBitmaps.value = updatedBitmaps
        }
    }

    private fun preheatThumbnails() {
        viewModelScope.launch(Dispatchers.Default) {
            val total = _pageCount.value
            val thumbs = _thumbnailBitmaps.value.toMutableMap()

            for (idx in 0 until total) {
                if (!thumbs.containsKey(idx)) {
                    synchronized(rendererLock) {
                        val renderer = currentPdfRenderer ?: return@launch
                        if (idx < renderer.pageCount) {
                            try {
                                val originalPage = renderer.openPage(idx)
                                try {
                                    val scale = 0.20f
                                    val w = (originalPage.width * scale).toInt().coerceAtLeast(60)
                                    val h = (originalPage.height * scale).toInt().coerceAtLeast(80)
                                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    val matrix = android.graphics.Matrix()
                                    matrix.setScale(scale, scale)
                                    originalPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    thumbs[idx] = bitmap
                                } finally {
                                    originalPage.close()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    _thumbnailBitmaps.value = thumbs.toMap()
                }
            }
        }
    }

    fun toggleDoublePageSpread() {
        _isDoublePageSpread.value = !_isDoublePageSpread.value
    }

    fun setVibeTheme(vibe: ReaderVibe) {
        _vibeTheme.value = vibe
    }

    fun toggleSidebar() {
        _isThumbnailSidebarOpen.value = !_isThumbnailSidebarOpen.value
    }

    // Bookmark operations
    fun togglePageBookmark(pageIdx: Int) {
        val path = _currentFilePath.value ?: return
        val currentBk = bookmarks.value.find { it.pageNumber == pageIdx }

        viewModelScope.launch(Dispatchers.IO) {
            if (currentBk != null) {
                repository.removeBookmark(currentBk.id)
            } else {
                repository.addBookmark(
                    BookmarkEntity(
                        documentPath = path,
                        pageNumber = pageIdx,
                        title = "Bookmark page ${pageIdx + 1}"
                    )
                )
            }
        }
    }

    // Annotations
    fun appendAnnotation(type: String, pageIdx: Int, dataJson: String, color: Int = Color.YELLOW) {
        val path = _currentFilePath.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addAnnotation(
                AnnotationEntity(
                    documentPath = path,
                    pageNumber = pageIdx,
                    type = type,
                    color = color,
                    dataJson = dataJson
                )
            )
        }
    }

    fun removeAnnotation(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeAnnotation(id)
        }
    }

    // Merge Document Picker logic
    fun queueToMerge(file: File) {
        val current = _filesToMerge.value.toMutableList()
        if (!current.contains(file)) {
            current.add(file)
            _filesToMerge.value = current
        }
    }

    fun dequeueFromMerge(file: File) {
        val current = _filesToMerge.value.toMutableList()
        current.remove(file)
        _filesToMerge.value = current
    }

    fun swapMergeFiles(fromIdx: Int, toIdx: Int) {
        val current = _filesToMerge.value.toMutableList()
        if (fromIdx in current.indices && toIdx in current.indices) {
            val temp = current[fromIdx]
            current[fromIdx] = current[toIdx]
            current[toIdx] = temp
            _filesToMerge.value = current
        }
    }

    fun clearMergeQueue() {
        _filesToMerge.value = emptyList()
    }

    // Share PDF
    fun shareDocument(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF: ${file.name}"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // PDF Actions executing on Background Coroutine Threadpool
    fun executeMerge(outputFileName: String, onCompleted: (File) -> Unit) {
        val fileList = _filesToMerge.value
        if (fileList.isEmpty()) return

        _progressStatus.value = "Merging ${fileList.size} documents..."
        viewModelScope.launch(Dispatchers.IO) {
            val documentsDir = getApplication<Application>().filesDir
            val safeName = if (outputFileName.endsWith(".pdf")) outputFileName else "$outputFileName.pdf"
            val mergedFile = File(documentsDir, safeName)
            PdfEngine.mergePdfs(fileList, mergedFile)
            _progressStatus.value = null
            loadDocument(mergedFile.absolutePath)
            withContext(Dispatchers.Main) {
                onCompleted(mergedFile)
            }
        }
    }

    fun executeSplit(pagesPerSplit: Int, onCompleted: (List<File>) -> Unit) {
        val path = _currentFilePath.value ?: return
        val originalFile = File(path)
        _progressStatus.value = "Splitting document every $pagesPerSplit pages..."

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = getApplication<Application>().filesDir
            val generatedSplits = PdfEngine.splitPdf(originalFile, outputDir, pagesPerSplit)
            _progressStatus.value = null
            generatedSplits.firstOrNull()?.let { loadDocument(it.absolutePath) }
            withContext(Dispatchers.Main) {
                onCompleted(generatedSplits)
            }
        }
    }

    fun executePagesExtraction(pageList: List<Int>, outputName: String, onCompleted: (File) -> Unit) {
        val path = _currentFilePath.value ?: return
        val originalFile = File(path)
        _progressStatus.value = "Extracting ${pageList.size} pages..."

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = getApplication<Application>().filesDir
            val safeName = if (outputName.endsWith(".pdf")) outputName else "$outputName.pdf"
            val extractedFile = File(outputDir, safeName)
            PdfEngine.extractPages(originalFile, extractedFile, pageList)
            _progressStatus.value = null
            loadDocument(extractedFile.absolutePath)
            withContext(Dispatchers.Main) {
                onCompleted(extractedFile)
            }
        }
    }

    fun executeRotation(degrees: Float, onCompleted: (File) -> Unit) {
        val path = _currentFilePath.value ?: return
        val originalFile = File(path)
        _progressStatus.value = "Rotating document pages by ${degrees.toInt()}°..."

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = getApplication<Application>().filesDir
            val rotatedFile = File(outputDir, "rotated_${originalFile.name}")
            PdfEngine.rotatePdfPages(originalFile, rotatedFile, degrees)
            _progressStatus.value = null
            loadDocument(rotatedFile.absolutePath)
            withContext(Dispatchers.Main) {
                onCompleted(rotatedFile)
            }
        }
    }

    fun executeCompression(qualitySlider: Float, onCompleted: (File) -> Unit) {
        val path = _currentFilePath.value ?: return
        val originalFile = File(path)
        _progressStatus.value = "Compressing document file size..."

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = getApplication<Application>().filesDir
            val compressedFile = File(outputDir, "compressed_${originalFile.name}")
            PdfEngine.compressPdf(originalFile, compressedFile, qualitySlider)
            _progressStatus.value = null
            loadDocument(compressedFile.absolutePath)
            withContext(Dispatchers.Main) {
                onCompleted(compressedFile)
            }
        }
    }

    fun executeWatermark(watermarkText: String, watermarkColor: Int, addPageNums: Boolean, headerText: String, footerText: String, onCompleted: (File) -> Unit) {
        val path = _currentFilePath.value ?: return
        val originalFile = File(path)
        _progressStatus.value = "Applying layout elements and watermarking..."

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = getApplication<Application>().filesDir
            val watermarkedFile = File(outputDir, "watermarked_${originalFile.name}")
            PdfEngine.applyWatermarkAndFeatures(
                originalFile, watermarkedFile,
                watermarkText, watermarkColor, addPageNums, headerText, footerText
            )
            _progressStatus.value = null
            loadDocument(watermarkedFile.absolutePath)
            withContext(Dispatchers.Main) {
                onCompleted(watermarkedFile)
            }
        }
    }

    fun executeImagesConversion(format: String, onCompleted: (List<File>) -> Unit) {
        val path = _currentFilePath.value ?: return
        val originalFile = File(path)
        _progressStatus.value = "Converting PDF pages to $format images..."

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = getApplication<Application>().filesDir
            val images = PdfEngine.pdfToImages(originalFile, outputDir, format)
            _progressStatus.value = null
            withContext(Dispatchers.Main) {
                onCompleted(images)
            }
        }
    }

    fun executeTextToPdfConversion(text: String, titleText: String, onCompleted: (File) -> Unit) {
        _progressStatus.value = "Compiling text source into document..."
        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = getApplication<Application>().filesDir
            val epoch = System.currentTimeMillis()
            val safeName = if (titleText.isNotBlank()) "${titleText.replace(" ", "_")}.pdf" else "text_doc_$epoch.pdf"
            val createdFile = File(outputDir, safeName)
            PdfEngine.convertTextToPdf(text, createdFile, titleText = titleText)
            _progressStatus.value = null
            loadDocument(createdFile.absolutePath)
            withContext(Dispatchers.Main) {
                onCompleted(createdFile)
            }
        }
    }

    fun executeImagesToPdfConversion(uris: List<Uri>, title: String, fitMode: PdfEngine.ImageFitMode, onCompleted: (File) -> Unit) {
        _progressStatus.value = "Compiling images to PDF..."
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val outputDir = context.filesDir
            val safeName = if (title.endsWith(".pdf")) title else "$title.pdf"
            val createdFile = File(outputDir, safeName)
            PdfEngine.convertImagesToPdf(context, uris, createdFile, fitMode = fitMode)
            _progressStatus.value = null
            loadDocument(createdFile.absolutePath)
            withContext(Dispatchers.Main) {
                onCompleted(createdFile)
            }
        }
    }

    fun executeDocxToPdfConversion(docxFile: File, onCompleted: (File) -> Unit) {
        _progressStatus.value = "Parsing and converting document..."
        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = getApplication<Application>().filesDir
            val createdFile = File(outputDir, "${docxFile.nameWithoutExtension}_from_docx.pdf")
            PdfEngine.convertDocxToPdf(docxFile, createdFile)
            _progressStatus.value = null
            loadDocument(createdFile.absolutePath)
            withContext(Dispatchers.Main) {
                onCompleted(createdFile)
            }
        }
    }

    fun executeWebUrlToPdfConversion(urlOrHtml: String, isUrl: Boolean, outputTitle: String, onCompleted: (File?) -> Unit) {
        _progressStatus.value = "Rendering HTML layout..."
        val context = getApplication<Application>()
        val outputDir = context.filesDir
        val safeName = if (outputTitle.endsWith(".pdf")) outputTitle else "$outputTitle.pdf"
        val createdFile = File(outputDir, safeName)

        PdfEngine.convertHtmlToPdf(context, urlOrHtml, isUrl, createdFile) { file ->
            _progressStatus.value = null
            if (file != null) {
                loadDocument(file.absolutePath)
            }
            onCompleted(file)
        }
    }

    fun inspectCurrentDocument(): String {
        val path = _currentFilePath.value ?: return "No document loaded."
        return PdfEngine.extractDocumentInfo(File(path))
    }

    private fun closeCurrentRendererInternal() {
        try {
            currentPdfRenderer?.close()
            currentPfd?.close()
            _pageBitmaps.value = emptyMap()
            _thumbnailBitmaps.value = emptyMap()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentPdfRenderer = null
        currentPfd = null
    }

    override fun onCleared() {
        super.onCleared()
        synchronized(rendererLock) {
            closeCurrentRendererInternal()
        }
    }
}
