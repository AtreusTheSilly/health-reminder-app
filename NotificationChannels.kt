package com.example.graduation

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Объект для создания каналов уведомлений
 * Начиная с Android Oreo, все уведомления должны быть привязаны к какому-либо каналу
 */
object NotificationChannels {

    const val REMINDER_CHANNEL_ID = "medication_reminder_channel"

    /**
     * Создает необходимые каналы уведомлений для приложения
     * Этот метод следует вызывать один раз при старте приложения, например, в Application.onCreate()
     */
    fun create(app: Application) {
        // Каналы нужны только для Android 8.0 (Oreo) и выше
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = app.getSystemService(NotificationManager::class.java)

            // Канал для основных напоминаний о приеме лекарств.
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Напоминания о лекарствах",
                // Высокий приоритет, чтобы уведомления были всплывающими и заметными
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для уведомлений о приеме лекарств"
            }

            notificationManager.createNotificationChannel(reminderChannel)
        }
    }
}
