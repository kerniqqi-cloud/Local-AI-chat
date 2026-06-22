package com.localai.chat.service.generation

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
import androidx.core.app.NotificationCompat
import com.localai.chat.MainActivity
import com.localai.chat.R

class GenerationForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        when (intent?.action) {
            ActionStart -> {
                val chatTitle = intent.getStringExtra(ExtraChatTitle).orEmpty().ifBlank { "Current chat" }
                val model = intent.getStringExtra(ExtraModel).orEmpty().ifBlank { "Selected model" }
                val notification = buildGeneratingNotification(chatTitle = chatTitle, model = model)
                startForegroundCompat(notification)
                return START_STICKY
            }
            ActionComplete -> {
                val chatTitle = intent.getStringExtra(ExtraChatTitle).orEmpty().ifBlank { "Current chat" }
                showFinalNotification(
                    title = "Response complete",
                    text = chatTitle,
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ActionFailure -> {
                val reason = intent.getStringExtra(ExtraReason).orEmpty().ifBlank { "Generation failed." }
                showFinalNotification(
                    title = "Generation failed",
                    text = reason,
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ActionStop -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        stopSelf()
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ActiveNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(ActiveNotificationId, notification)
        }
    }

    private fun buildGeneratingNotification(
        chatTitle: String,
        model: String,
    ): Notification = NotificationCompat.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Generating response...")
        .setContentText("$chatTitle • $model")
        .setStyle(NotificationCompat.BigTextStyle().bigText("Streaming response in $chatTitle using $model."))
        .setContentIntent(openAppPendingIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun showFinalNotification(
        title: String,
        text: String,
    ) {
        val notification = NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(FinalNotificationId, notification)
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            "Generation",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows active local model response generation."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val ChannelId = "generation"
        private const val ActiveNotificationId = 1001
        private const val FinalNotificationId = 1002

        private const val ActionStart = "com.localai.chat.action.START_GENERATION"
        private const val ActionComplete = "com.localai.chat.action.COMPLETE_GENERATION"
        private const val ActionFailure = "com.localai.chat.action.FAIL_GENERATION"
        private const val ActionStop = "com.localai.chat.action.STOP_GENERATION"

        private const val ExtraChatTitle = "extra_chat_title"
        private const val ExtraModel = "extra_model"
        private const val ExtraReason = "extra_reason"

        fun startIntent(context: Context, chatTitle: String, model: String): Intent = Intent(
            context,
            GenerationForegroundService::class.java,
        ).apply {
            action = ActionStart
            putExtra(ExtraChatTitle, chatTitle)
            putExtra(ExtraModel, model)
        }

        fun completeIntent(context: Context, chatTitle: String): Intent = Intent(
            context,
            GenerationForegroundService::class.java,
        ).apply {
            action = ActionComplete
            putExtra(ExtraChatTitle, chatTitle)
        }

        fun failureIntent(context: Context, reason: String): Intent = Intent(
            context,
            GenerationForegroundService::class.java,
        ).apply {
            action = ActionFailure
            putExtra(ExtraReason, reason)
        }

        fun stopIntent(context: Context): Intent = Intent(
            context,
            GenerationForegroundService::class.java,
        ).apply {
            action = ActionStop
        }
    }
}
