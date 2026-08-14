package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PdfViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val pdfViewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingPdf(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PdfNavigationWrapper(viewModel = pdfViewModel, incomingUri = intent?.data)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingPdf(intent)
    }

    private fun handleIncomingPdf(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && intent.type?.contains("pdf", ignoreCase = true) == true) {
            importPdfUri(this, uri, pdfViewModel)
        }
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "document_${System.currentTimeMillis()}.pdf"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val retrievedName = cursor.getString(nameIndex)
                if (!retrievedName.isNullOrBlank()) {
                    name = retrievedName
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (!name.endsWith(".pdf", ignoreCase = true)) {
        name = "$name.pdf"
    }
    return name
}

fun importPdfUri(context: Context, uri: Uri, viewModel: PdfViewModel): String? {
    return try {
        val fileName = getFileNameFromUri(context, uri)
        val destinationFile = File(context.filesDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            destinationFile.outputStream().use { stream.copyTo(it) }
        }
        viewModel.loadDocument(destinationFile.absolutePath)
        destinationFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun PdfNavigationWrapper(viewModel: PdfViewModel, incomingUri: Uri? = null) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            val loadedPath = importPdfUri(context, incomingUri, viewModel)
            if (loadedPath != null) {
                navController.navigate("reader")
            }
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val loadedPath = importPdfUri(context, uri, viewModel)
            if (loadedPath != null) {
                navController.navigate("reader")
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeDashboard(
                viewModel = viewModel,
                onNavigateToReader = { path ->
                    viewModel.loadDocument(path)
                    navController.navigate("reader")
                },
                onNavigateToCreator = { tab ->
                    navController.navigate("creator/$tab")
                },
                onNavigateToMergeSplit = {
                    navController.navigate("mergesplit")
                },
                onNavigateToUtilities = {
                    navController.navigate("utilities")
                },
                onPickFile = {
                    docPickerLauncher.launch("application/pdf")
                }
            )
        }

        composable("reader") {
            PdfReaderScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "creator/{tab}",
            arguments = listOf(navArgument("tab") { type = NavType.StringType })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: "images"
            PdfCreatorScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                initialTab = tab
            )
        }

        composable("mergesplit") {
            PdfMergeSplitScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("utilities") {
            PdfUtilitiesScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
