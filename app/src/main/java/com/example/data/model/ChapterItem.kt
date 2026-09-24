package com.example.data.model

data class ChapterItem(
    val title: String,
    val pageIndex: Int,
    val level: Int = 0,
    val children: List<ChapterItem> = emptyList()
)
