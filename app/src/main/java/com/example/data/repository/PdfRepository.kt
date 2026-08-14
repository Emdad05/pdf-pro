package com.example.data.repository

import com.example.data.database.PdfDao
import com.example.data.database.RecentDocument
import com.example.data.database.BookmarkEntity
import com.example.data.database.AnnotationEntity
import kotlinx.coroutines.flow.Flow

class PdfRepository(private val pdfDao: PdfDao) {
    // Recent Documents
    val recentDocuments: Flow<List<RecentDocument>> = pdfDao.getRecentDocuments()

    suspend fun addRecentDocument(document: RecentDocument) {
        pdfDao.insertRecentDocument(document)
    }

    suspend fun removeRecentDocument(filePath: String) {
        pdfDao.deleteRecentDocument(filePath)
    }

    // Bookmarks
    fun getBookmarksForDocument(documentPath: String): Flow<List<BookmarkEntity>> {
        return pdfDao.getBookmarksForDocument(documentPath)
    }

    suspend fun addBookmark(bookmark: BookmarkEntity) {
        pdfDao.insertBookmark(bookmark)
    }

    suspend fun removeBookmark(id: Int) {
        pdfDao.deleteBookmark(id)
    }

    suspend fun removeBookmarkForPage(documentPath: String, pageNumber: Int) {
        pdfDao.deleteBookmarkForPage(documentPath, pageNumber)
    }

    // Annotations
    fun getAnnotationsForDocument(documentPath: String): Flow<List<AnnotationEntity>> {
        return pdfDao.getAnnotationsForDocument(documentPath)
    }

    fun getAnnotationsForPage(documentPath: String, pageNumber: Int): Flow<List<AnnotationEntity>> {
        return pdfDao.getAnnotationsForPage(documentPath, pageNumber)
    }

    suspend fun addAnnotation(annotation: AnnotationEntity) {
        pdfDao.insertAnnotation(annotation)
    }

    suspend fun removeAnnotation(id: Int) {
        pdfDao.deleteAnnotation(id)
    }
}
