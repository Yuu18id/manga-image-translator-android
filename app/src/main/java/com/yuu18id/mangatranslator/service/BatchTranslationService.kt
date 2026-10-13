package com.yuu18id.mangatranslator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.yuu18id.mangatranslator.MainActivity
import com.yuu18id.mangatranslator.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BatchTranslationService : Service() {

    @Inject
    lateinit var batchTranslationManager: BatchTranslationManager

    companion object {
        const val CHANNEL_ID = "batch_translation_channel"
        const val NOTIFICATION_ID = 4001
        const val COMPLETE_NOTIFICATION_ID = 4002

        const val ACTION_START = "com.yuu18id.mangatranslator.action.START_BATCH_SERVICE"
        const val ACTION_UPDATE = "com.yuu18id.mangatranslator.action.UPDATE_BATCH_PROGRESS"
        const val ACTION_STOP = "com.yuu18id.mangatranslator.action.STOP_BATCH_SERVICE"
        const val ACTION_CANCEL = "com.yuu18id.mangatranslator.action.CANCEL_BATCH"

        const val EXTRA_TOTAL = "extra_total"
        const val EXTRA_COMPLETED = "extra_completed"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STAGE_MESSAGE = "extra_stage_message"
        const val EXTRA_IS_SUCCESS = "extra_is_success"

        fun start(context: Context, total: Int) {
            val intent = Intent(context, BatchTranslationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TOTAL, total)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                // Handles ForegroundServiceStartNotAllowedException on Android 12+ (API 31+)
                // and IllegalStateException when invoked while backgrounded.
                android.util.Log.e("BatchTranslationService", "Could not start foreground batch service: ${e.message}", e)
            }
        }

        fun updateProgress(context: Context, completed: Int, total: Int, progress: Float, message: String) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val notification = buildOngoingNotification(context, completed, total, progress, message)
                notificationManager?.notify(NOTIFICATION_ID, notification)
            } catch (_: Throwable) {}
        }

        fun stop(context: Context, isSuccess: Boolean = true, completed: Int = 0, total: Int = 0) {
            try {
                val intent = Intent(context, BatchTranslationService::class.java).apply {
                    action = ACTION_STOP
                    putExtra(EXTRA_IS_SUCCESS, isSuccess)
                    putExtra(EXTRA_COMPLETED, completed)
                    putExtra(EXTRA_TOTAL, total)
                }
                context.startService(intent)
            } catch (_: IllegalStateException) {
                // If app is in background and cannot startService, cancel ongoing notification
                // and display completion notification directly.
                try {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(NOTIFICATION_ID)
                    if (isSuccess && completed > 0) {
                        val completeNotification = buildCompleteNotification(context, completed, total)
                        nm?.notify(COMPLETE_NOTIFICATION_ID, completeNotification)
                    }
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }

        fun buildOngoingNotification(context: Context, completed: Int, total: Int, progress: Float, message: String): Notification {
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelIntent = Intent(context, BatchTranslationService::class.java).apply {
                action = ACTION_CANCEL
            }
            val cancelPendingIntent = PendingIntent.getService(
                context,
                1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val progressPercent = (progress * 100).toInt().coerceIn(0, 100)
            val contentText = if (message.isNotBlank()) {
                context.getString(R.string.notification_batch_progress_with_stage, completed, total, progressPercent, message)
            } else {
                context.getString(R.string.notification_batch_progress, completed, total, progressPercent)
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notification_batch_title))
                .setContentText(contentText)
                .setProgress(100, progressPercent, false)
                .setOngoing(true)
                .setContentIntent(contentPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.action_cancel),
                    cancelPendingIntent
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build()
        }

        fun buildCompleteNotification(context: Context, completed: Int, total: Int): Notification {
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notification_batch_complete_title))
                .setContentText(context.getString(R.string.notification_batch_complete_msg, completed, total))
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var isForegroundActive = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        acquireWakeLock()

        // Monitor batch processing state to self-terminate gracefully when batch completes or cancels
        serviceScope.launch {
            batchTranslationManager.uiState.collect { state ->
                if (!state.isProcessing && isForegroundActive) {
                    val completed = state.completedCount
                    val total = state.pages.size
                    val isSuccess = state.failedCount == 0 && completed > 0
                    handleStop(isSuccess, completed, total)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val total = intent.getIntExtra(EXTRA_TOTAL, 0)
                val notification = buildOngoingNotification(this, 0, total, 0f, "")
                startForegroundCompat(notification)
            }
            ACTION_UPDATE -> {
                val completed = intent.getIntExtra(EXTRA_COMPLETED, 0)
                val total = intent.getIntExtra(EXTRA_TOTAL, 0)
                val progress = intent.getFloatExtra(EXTRA_PROGRESS, 0f)
                val message = intent.getStringExtra(EXTRA_STAGE_MESSAGE) ?: ""
                val notification = buildOngoingNotification(this, completed, total, progress, message)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_CANCEL -> {
                batchTranslationManager.cancelBatchTranslation()
                handleStop(isSuccess = false, completed = 0, total = 0)
            }
            ACTION_STOP -> {
                val isSuccess = intent.getBooleanExtra(EXTRA_IS_SUCCESS, true)
                val completed = intent.getIntExtra(EXTRA_COMPLETED, 0)
                val total = intent.getIntExtra(EXTRA_TOTAL, 0)
                handleStop(isSuccess, completed, total)
            }
        }

        return START_NOT_STICKY
    }

    private fun handleStop(isSuccess: Boolean, completed: Int, total: Int) {
        if (!isForegroundActive) return
        isForegroundActive = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (isSuccess && completed > 0) {
            val completeNotification = buildCompleteNotification(this, completed, total)
            notificationManager.notify(COMPLETE_NOTIFICATION_ID, completeNotification)
        }

        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        isForegroundActive = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MangaTranslator:BatchTranslationWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L) // 30 minutes safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_batch_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_batch_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
