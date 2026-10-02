package com.malaramofficial.mobilestudio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.malaramofficial.mobilestudio.R
import com.malaramofficial.mobilestudio.MainActivity
import com.malaramofficial.mobilestudio.MalaramStudioApplication
import com.malaramofficial.mobilestudio.engine.screen.MediaProjectionCaptureController
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Foreground Service holding the multimedia broadcast session.
 * Phase 0 establishes the production service lifecycle, notification channels,
 * and Binder architecture without triggering fake streaming or capture.
 */
class StudioService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val binder = LocalBinder()
    private var screenCaptureController: MediaProjectionCaptureController? = null

    inner class LocalBinder : Binder() {
        fun getService(): StudioService = this@StudioService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_LIVE -> {
                startInForeground(includeMediaProjection = false)
                val endpoint = intent?.getStringExtra(EXTRA_RTMP_ENDPOINT).orEmpty()
                if (endpoint.isNotBlank()) {
                    try {
                        val app = application as MalaramStudioApplication
                        app.broadcastController.start(endpoint)
                    } catch (t: Throwable) {
                        appErrorLog(t)
                        stopForegroundService()
                    }
                }
            }
            ACTION_START_RECORDING -> {
                startInForeground(includeMediaProjection = false)
                try {
                    val app = application as MalaramStudioApplication
                    serviceScope.launch {
                        app.recordingController.startRecording(RecordingConfig())
                    }
                } catch (t: Throwable) {
                    stopSelf()
                }
            }
            ACTION_STOP_RECORDING -> {
                (application as MalaramStudioApplication).recordingController.let { controller ->
                    serviceScope.launch {
                        controller.stopRecording()
                        if (!(application as MalaramStudioApplication).broadcastController.state.value.isBroadcasting) {
                            stopForegroundService()
                        }
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_PAUSE_RECORDING -> {
                serviceScope.launch {
                    (application as MalaramStudioApplication).recordingController.pauseRecording()
                }
            }
            ACTION_RESUME_RECORDING -> {
                serviceScope.launch {
                    (application as MalaramStudioApplication).recordingController.resumeRecording()
                }
            }
            ACTION_STOP_LIVE -> {
                (application as MalaramStudioApplication).broadcastController.stop()
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START_FOREGROUND -> {
                startInForeground(includeMediaProjection = false)
            }
            ACTION_START_SCREEN_CAPTURE -> {
                startInForeground(includeMediaProjection = true)
                startScreenCaptureFromIntent(intent)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun appErrorLog(t: Throwable) {
        android.util.Log.e("MalaramStudioService", "Studio session failed", t)
    }

    private fun startInForeground(includeMediaProjection: Boolean) {
        val notification = buildStudioNotification("Studio Engine Ready", "Standing by in background")

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (includeMediaProjection) {
                // Screen capture sessions must explicitly run under the
                // mediaProjection foreground-service type.
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                serviceType
            )
        } catch (e: Exception) {
            throw IllegalStateException("Unable to start Studio foreground service", e)
        }
    }

    private fun startScreenCaptureFromIntent(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        }
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
        val width = intent.getIntExtra(EXTRA_CAPTURE_WIDTH, 1080)
        val height = intent.getIntExtra(EXTRA_CAPTURE_HEIGHT, 1920)
        val densityDpi = intent.getIntExtra(EXTRA_DENSITY_DPI, resources.displayMetrics.densityDpi)

        if (resultCode == Int.MIN_VALUE || resultData == null || sourceId.isNullOrBlank()) {
            return
        }

        val app = application as MalaramStudioApplication
        screenCaptureController?.stop()

        app.renderPipeline.createScreenCaptureInput(sourceId, width, height) { input ->
            try {
                screenCaptureController = MediaProjectionCaptureController(this) {
                    app.renderPipeline.releaseScreenCaptureInputForAnyScreenSource()
                }
                screenCaptureController?.start(
                    resultCode = resultCode,
                    resultData = resultData,
                    surface = input.surface,
                    width = width,
                    height = height,
                    densityDpi = densityDpi
                )
            } catch (t: Throwable) {
                app.renderPipeline.releaseScreenCaptureInput(sourceId)
                screenCaptureController?.stop()
                screenCaptureController = null
            }
        }
    }

    private fun stopForegroundService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        screenCaptureController?.stop()
        screenCaptureController = null
        (application as? MalaramStudioApplication)?.broadcastController?.stop()
        (application as? MalaramStudioApplication)?.recordingController?.let { controller ->
            serviceScope.launch { controller.stopRecording() }
        }
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun buildStudioNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Malaram Studio Active Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live broadcast and studio recording status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "malaram_studio_broadcast_channel"
        const val ACTION_START_FOREGROUND = "com.malaramofficial.mobilestudio.ACTION_START_FOREGROUND"
        const val ACTION_START_SCREEN_CAPTURE = "com.malaramofficial.mobilestudio.ACTION_START_SCREEN_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_SOURCE_ID = "extra_source_id"
        const val EXTRA_CAPTURE_WIDTH = "extra_capture_width"
        const val EXTRA_CAPTURE_HEIGHT = "extra_capture_height"
        const val EXTRA_DENSITY_DPI = "extra_density_dpi"
        const val ACTION_STOP_SERVICE = "com.malaramofficial.mobilestudio.ACTION_STOP_SERVICE"
        const val ACTION_START_LIVE = "com.malaramofficial.mobilestudio.ACTION_START_LIVE"
        const val ACTION_STOP_LIVE = "com.malaramofficial.mobilestudio.ACTION_STOP_LIVE"
        const val EXTRA_RTMP_ENDPOINT = "extra_rtmp_endpoint"
        const val ACTION_START_RECORDING = "com.malaramofficial.mobilestudio.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.malaramofficial.mobilestudio.ACTION_STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.malaramofficial.mobilestudio.ACTION_PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.malaramofficial.mobilestudio.ACTION_RESUME_RECORDING"
    }
}
