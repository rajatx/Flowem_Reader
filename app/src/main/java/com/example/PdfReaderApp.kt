package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.db.AppDatabase
import com.example.data.pdf.PdfExtractor
import com.example.data.repository.BookRepository
import com.example.data.repository.SettingsRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfReaderApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var bookRepository: BookRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var pdfExtractor: PdfExtractor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            PDFBoxResourceLoader.init(applicationContext)
        } catch (ignored: Exception) {}

        database = AppDatabase.getDatabase(this)
        bookRepository = BookRepository(database.bookDao())
        settingsRepository = SettingsRepository(this)
        pdfExtractor = PdfExtractor(this)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                READ_ALOUD_CHANNEL_ID,
                "Read Aloud Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for reading PDF aloud when screen is off"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val READ_ALOUD_CHANNEL_ID = "read_aloud_channel"
        lateinit var instance: PdfReaderApp
            private set
    }
}
