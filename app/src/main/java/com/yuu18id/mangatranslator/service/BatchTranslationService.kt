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

class BatchTranslationService : Service() {

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

        var onCancelRequested: (() -> Unit)? = null

        fun start(context: Context, total: Int) {
            val intent = Intent(context, BatchTranslationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TOTAL, total)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, completed: Int, total: Int, progress: Float, message: String) {
            val intent = Intent(context, BatchTranslationService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_COMPLETED, completed)
                putExtra(EXTRA_TOTAL, total)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_STAGE_MESSAGE, message)
            }
            context.startService(intent)
        }

        fun stop(context: Context, isSuccess: Boolean = true, completed: Int = 0, total: Int = 0) {
            val intent = Intent(context, BatchTranslationService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_IS_SUCCESS, isSuccess)
                putExtra(EXTRA_COMPLETED, completed)
                putExtra(EXTRA_TOTAL, total)
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val total = intent.getIntExtra(EXTRA_TOTAL, 0)
                val notification = buildOngoingNotification(0, total, 0f, "")
                startForegroundCompat(notification)
            }
            ACTION_UPDATE -> {
                val completed = intent.getIntExtra(EXTRA_COMPLETED, 0)
                val total = intent.getIntExtra(EXTRA_TOTAL, 0)
                val progress = intent.getFloatExtra(EXTRA_PROGRESS, 0f)
                val message = intent.getStringExtra(EXTRA_STAGE_MESSAGE) ?: ""
                val notification = buildOngoingNotification(completed, total, progress, message)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_CANCEL -> {
                onCancelRequested?.invoke()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_STOP -> {
                val isSuccess = intent.getBooleanExtra(EXTRA_IS_SUCCESS, true)
                val completed = intent.getIntExtra(EXTRA_COMPLETED, 0)
                val total = intent.getIntExtra(EXTRA_TOTAL, 0)

                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)

                if (isSuccess && completed > 0) {
                    val completeNotification = buildCompleteNotification(completed, total)
                    notificationManager.notify(COMPLETE_NOTIFICATION_ID, completeNotification)
                }

                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
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

    private fun buildOngoingNotification(completed: Int, total: Int, progress: Float, message: String): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, BatchTranslationService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progressPercent = (progress * 100).toInt().coerceIn(0, 100)
        val contentText = if (message.isNotBlank()) {
            getString(R.string.notification_batch_progress_with_stage, completed, total, progressPercent, message)
        } else {
            getString(R.string.notification_batch_progress, completed, total, progressPercent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_batch_title))
            .setContentText(contentText)
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_cancel),
                cancelPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun buildCompleteNotification(completed: Int, total: Int): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_batch_complete_title))
            .setContentText(getString(R.string.notification_batch_complete_msg, completed, total))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
}
