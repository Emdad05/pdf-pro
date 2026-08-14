package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_documents")
data class RecentDocument(
    @PrimaryKey val filePath: String,
    val title: String,
    val lastPageRead: Int,
    val totalPages: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val documentPath: String,
    val pageNumber: Int,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val documentPath: String,
    val pageNumber: Int,
    val type: String, // HIGHLIGHT, UNDERLINE, STRIKETHROUGH, DRAWING, NOTE, STAMP
    val color: Int, // ARGB color
    val dataJson: String, // JSON serialization of coordinate points or content text
    val timestamp: Long = System.currentTimeMillis()
)
