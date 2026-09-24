package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val uriString: String,
    val title: String,
    val author: String? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val lastZoomLevel: Float = 1.0f,
    val isFinished: Boolean = false,
    val totalReadingSeconds: Long = 0L,
    val readingMode: String = "LIGHT",
    val scrollMode: String = "VERTICAL",
    val coverImagePath: String? = null
)
