package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppSettings
import com.example.data.model.AppThemeMode
import com.example.data.model.ReadingMode
import com.example.data.model.ScrollMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val themeStr = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val readingModeStr = prefs.getString("default_reading_mode", ReadingMode.LIGHT.name) ?: ReadingMode.LIGHT.name
        val scrollModeStr = prefs.getString("default_scroll_mode", ScrollMode.VERTICAL.name) ?: ScrollMode.VERTICAL.name

        return AppSettings(
            themeMode = try { AppThemeMode.valueOf(themeStr) } catch (e: Exception) { AppThemeMode.SYSTEM },
            defaultReadingMode = try { ReadingMode.valueOf(readingModeStr) } catch (e: Exception) { ReadingMode.LIGHT },
            defaultScrollMode = try { ScrollMode.valueOf(scrollModeStr) } catch (e: Exception) { ScrollMode.VERTICAL },
            volumeKeysTurnPages = prefs.getBoolean("volume_keys_turn_pages", true),
            keepScreenOn = prefs.getBoolean("keep_screen_on", true),
            autoOffMinutes = prefs.getInt("auto_off_minutes", 10),
            breakReminderMinutes = prefs.getInt("break_reminder_minutes", 20),
            warmFilterStrength = prefs.getFloat("warm_filter_strength", 0.0f),
            autoCropMargins = prefs.getBoolean("auto_crop_margins", false)
        )
    }

    fun updateThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _settings.update { it.copy(themeMode = mode) }
    }

    fun updateDefaultReadingMode(mode: ReadingMode) {
        prefs.edit().putString("default_reading_mode", mode.name).apply()
        _settings.update { it.copy(defaultReadingMode = mode) }
    }

    fun updateDefaultScrollMode(mode: ScrollMode) {
        prefs.edit().putString("default_scroll_mode", mode.name).apply()
        _settings.update { it.copy(defaultScrollMode = mode) }
    }

    fun updateVolumeKeys(enabled: Boolean) {
        prefs.edit().putBoolean("volume_keys_turn_pages", enabled).apply()
        _settings.update { it.copy(volumeKeysTurnPages = enabled) }
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
        _settings.update { it.copy(keepScreenOn = enabled) }
    }

    fun updateAutoOffMinutes(minutes: Int) {
        prefs.edit().putInt("auto_off_minutes", minutes).apply()
        _settings.update { it.copy(autoOffMinutes = minutes) }
    }

    fun updateBreakReminder(minutes: Int) {
        prefs.edit().putInt("break_reminder_minutes", minutes).apply()
        _settings.update { it.copy(breakReminderMinutes = minutes) }
    }

    fun updateWarmFilterStrength(strength: Float) {
        prefs.edit().putFloat("warm_filter_strength", strength).apply()
        _settings.update { it.copy(warmFilterStrength = strength) }
    }

    fun updateAutoCropMargins(enabled: Boolean) {
        prefs.edit().putBoolean("auto_crop_margins", enabled).apply()
        _settings.update { it.copy(autoCropMargins = enabled) }
    }
}
