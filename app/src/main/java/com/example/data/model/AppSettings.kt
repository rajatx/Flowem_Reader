package com.example.data.model

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultReadingMode: ReadingMode = ReadingMode.LIGHT,
    val defaultScrollMode: ScrollMode = ScrollMode.VERTICAL,
    val volumeKeysTurnPages: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoOffMinutes: Int = 10,
    val breakReminderMinutes: Int = 20, // 0 = off, 20, 30, 45
    val warmFilterStrength: Float = 0.0f,
    val autoCropMargins: Boolean = false
)
