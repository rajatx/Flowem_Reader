package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.PdfReaderApp
import com.example.R
import com.example.data.pdf.PdfExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class ReadAloudService : Service(), TextToSpeech.OnInitListener {

    private val binder = LocalBinder()
    private var textToSpeech: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var currentUri: Uri? = null
    private var bookTitle: String = "Reading Aloud"
    private var totalPages: Int = 1
    private var currentPage: Int = 0
    private var speechSpeed: Float = 1.0f
    private var isTtsReady = false

    data class PlayerState(
        val isPlaying: Boolean = false,
        val pageIndex: Int = 0,
        val totalPages: Int = 1,
        val speed: Float = 1.0f,
        val bookTitle: String = ""
    )

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): ReadAloudService = this@ReadAloudService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        textToSpeech = TextToSpeech(this, this)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PdfReader::ReadAloudWakeLock")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _playerState.value = _playerState.value.copy(isPlaying = true)
                    updateNotification()
                }

                override fun onDone(utteranceId: String?) {
                    // Page reading complete, advance to next page if available
                    serviceScope.launch {
                        if (currentPage < totalPages - 1 && _playerState.value.isPlaying) {
                            nextPage()
                        } else {
                            pause()
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    pause()
                }
            })
            isTtsReady = true
            setSpeed(speechSpeed)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> nextPage()
            ACTION_PREV -> prevPage()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    fun startReading(uri: Uri, title: String, startPage: Int, pagesCount: Int) {
        currentUri = uri
        bookTitle = title
        totalPages = pagesCount
        currentPage = startPage
        _playerState.value = PlayerState(
            isPlaying = true,
            pageIndex = currentPage,
            totalPages = totalPages,
            speed = speechSpeed,
            bookTitle = bookTitle
        )
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification())
        readCurrentPage()
    }

    fun resume() {
        if (!_playerState.value.isPlaying) {
            _playerState.value = _playerState.value.copy(isPlaying = true)
            acquireWakeLock()
            updateNotification()
            readCurrentPage()
        }
    }

    fun pause() {
        textToSpeech?.stop()
        _playerState.value = _playerState.value.copy(isPlaying = false)
        releaseWakeLock()
        updateNotification()
    }

    fun nextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++
            _playerState.value = _playerState.value.copy(pageIndex = currentPage)
            if (_playerState.value.isPlaying) {
                readCurrentPage()
            }
            updateNotification()
        }
    }

    fun prevPage() {
        if (currentPage > 0) {
            currentPage--
            _playerState.value = _playerState.value.copy(pageIndex = currentPage)
            if (_playerState.value.isPlaying) {
                readCurrentPage()
            }
            updateNotification()
        }
    }

    fun setSpeed(speed: Float) {
        speechSpeed = speed
        textToSpeech?.setSpeechRate(speed)
        _playerState.value = _playerState.value.copy(speed = speed)
    }

    fun stopPlayback() {
        pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun readCurrentPage() {
        val uri = currentUri ?: return
        serviceScope.launch {
            val extractor = PdfExtractor(applicationContext)
            val text = extractor.extractTextForPage(uri, currentPage)
            if (text.isNotBlank() && isTtsReady) {
                val params = android.os.Bundle()
                textToSpeech?.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    params,
                    "PAGE_${currentPage}_${System.currentTimeMillis()}"
                )
            } else {
                // If page text is empty or scan only, briefly announce page number or pause
                textToSpeech?.speak(
                    "Page ${currentPage + 1}",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "PAGE_EMPTY_${currentPage}"
                )
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(60 * 60 * 1000L) // 60 minutes
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingAppIntent = PendingIntent.getActivity(
            this, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (_playerState.value.isPlaying) {
            val pauseIntent = Intent(this, ReadAloudService::class.java).apply { action = ACTION_PAUSE }
            val pausePending = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pausePending)
        } else {
            val playIntent = Intent(this, ReadAloudService::class.java).apply { action = ACTION_PLAY }
            val playPending = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", playPending)
        }

        val nextIntent = Intent(this, ReadAloudService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE)
        val nextAction = NotificationCompat.Action(android.R.drawable.ic_media_next, "Next Page", nextPending)

        return NotificationCompat.Builder(this, PdfReaderApp.READ_ALOUD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(bookTitle)
            .setContentText("Page ${currentPage + 1} of $totalPages (${speechSpeed}x speed)")
            .setContentIntent(pendingAppIntent)
            .setOngoing(_playerState.value.isPlaying)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Reading page ${currentPage + 1} of $totalPages aloud at ${speechSpeed}x speed.")
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    companion object {
        const val NOTIFICATION_ID = 2048
        const val ACTION_PLAY = "com.example.pdfreader.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.pdfreader.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.pdfreader.ACTION_NEXT"
        const val ACTION_PREV = "com.example.pdfreader.ACTION_PREV"
        const val ACTION_STOP = "com.example.pdfreader.ACTION_STOP"
    }
}
