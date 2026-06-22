package com.localai.chat.service.generation

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerationForegroundServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start(chatTitle: String, model: String): Boolean {
        return runCatching {
            ContextCompat.startForegroundService(
                context,
                GenerationForegroundService.startIntent(context, chatTitle, model),
            )
        }.isSuccess
    }

    fun complete(chatTitle: String) {
        runCatching {
            context.startService(GenerationForegroundService.completeIntent(context, chatTitle))
        }
    }

    fun failure(reason: String) {
        runCatching {
            context.startService(GenerationForegroundService.failureIntent(context, reason))
        }
    }

    fun stop() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startService(GenerationForegroundService.stopIntent(context))
            } else {
                context.stopService(GenerationForegroundService.stopIntent(context))
            }
        }
    }
}
