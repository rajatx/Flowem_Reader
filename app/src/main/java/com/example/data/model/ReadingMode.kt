package com.example.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

enum class ReadingMode(
    val title: String,
    val backgroundColor: Color,
    val contentColor: Color,
    val barColor: Color
) {
    LIGHT(
        title = "Light",
        backgroundColor = Color(0xFFF9F9F8),
        contentColor = Color(0xFF1E2022),
        barColor = Color(0xFFFFFFFF)
    ),
    SEPIA(
        title = "Sepia",
        backgroundColor = Color(0xFFF5ECD7),
        contentColor = Color(0xFF3E2A18),
        barColor = Color(0xFFEFE4CB)
    ),
    DARK(
        title = "Dark",
        backgroundColor = Color(0xFF1E2022),
        contentColor = Color(0xFFE2E2E6),
        barColor = Color(0xFF16181A)
    ),
    NIGHT(
        title = "Night",
        backgroundColor = Color(0xFF000000),
        contentColor = Color(0xFFB0A898),
        barColor = Color(0xFF0D0E10)
    );

    fun getColorFilter(smartInvert: Boolean = true): ColorFilter? {
        return when (this) {
            LIGHT -> null
            SEPIA -> {
                // Transforms white (255,255,255) -> warm parchment #F5ECD7 (245, 236, 215)
                // Transforms black (0,0,0) -> warm dark brown #3E2A18 (62, 42, 24)
                val matrix = ColorMatrix(
                    floatArrayOf(
                        0.718f, 0f, 0f, 0f, 62f,
                        0f, 0.761f, 0f, 0f, 42f,
                        0f, 0f, 0.749f, 0f, 24f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
            DARK -> {
                // Invert: transforms white (255) -> dark slate #1E2022 (30, 32, 34)
                // transforms black (0) -> crisp light grey #E2E2E6 (226, 226, 230)
                val matrix = ColorMatrix(
                    floatArrayOf(
                        -0.768f, 0f, 0f, 0f, 226f,
                        0f, -0.760f, 0f, 0f, 226f,
                        0f, 0f, -0.768f, 0f, 230f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
            NIGHT -> {
                // OLED True Black: transforms white (255) -> deep black #0A0A0A (10, 10, 10)
                // transforms black (0) -> soft eye-safe amber grey #B0A898 (176, 168, 152)
                val matrix = ColorMatrix(
                    floatArrayOf(
                        -0.651f, 0f, 0f, 0f, 176f,
                        0f, -0.620f, 0f, 0f, 168f,
                        0f, 0f, -0.557f, 0f, 152f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
        }
    }
}
