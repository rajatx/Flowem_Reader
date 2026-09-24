package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String,
    val pageNumber: Int,
    val selectedText: String,
    val colorHex: String = "#FFE082", // Default soft yellow
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
