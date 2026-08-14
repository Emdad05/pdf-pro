package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    // Recent Documents
    @Query("SELECT * FROM recent_documents ORDER BY timestamp DESC")
    fun getRecentDocuments(): Flow<List<RecentDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentDocument(document: RecentDocument)

    @Query("DELETE FROM recent_documents WHERE filePath = :filePath")
    suspend fun deleteRecentDocument(filePath: String)

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE documentPath = :documentPath ORDER BY pageNumber ASC")
    fun getBookmarksForDocument(documentPath: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Int)

    @Query("DELETE FROM bookmarks WHERE documentPath = :documentPath AND pageNumber = :pageNumber")
    suspend fun deleteBookmarkForPage(documentPath: String, pageNumber: Int)

    // Annotations
    @Query("SELECT * FROM annotations WHERE documentPath = :documentPath ORDER BY id ASC")
    fun getAnnotationsForDocument(documentPath: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE documentPath = :documentPath AND pageNumber = :pageNumber ORDER BY id ASC")
    fun getAnnotationsForPage(documentPath: String, pageNumber: Int): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: Int)
}
