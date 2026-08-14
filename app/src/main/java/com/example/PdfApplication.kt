package com.example

import android.app.Application
import com.example.data.database.PdfDatabase
import com.example.data.repository.PdfRepository

class PdfApplication : Application() {
    val database: PdfDatabase by lazy { PdfDatabase.getDatabase(this) }
    val repository: PdfRepository by lazy { PdfRepository(database.pdfDao) }
}
