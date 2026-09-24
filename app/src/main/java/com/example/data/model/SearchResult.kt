package com.example.data.model

data class SearchResult(
    val pageIndex: Int,
    val pageNumber: Int,
    val snippet: String,
    val startIndex: Int,
    val length: Int
)
