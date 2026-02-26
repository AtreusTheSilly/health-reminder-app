package com.example.graduation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * BroadcastReceiver, который слушает срабатывания системных будильников
 * Является точкой входа для отображения уведомлений
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        // Уникальное действие для интента, который создается кнопкой "Отложить" в уведомлении
        const val SNOOZE_ACTION = "com.example.graduation.action.SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getIntExtra("MEDICATION_ID", -1)
        if (medicationId == -1) return

        // Если интент пришел от кнопки "Отложить"
        if (intent.action == SNOOZE_ACTION) {
            // Просто планируем новый будильник через 10 минут
            AlarmScheduler(context).scheduleSnooze(medicationId)
            // Скрываем исходное уведомление
            NotificationManagerCompat.from(context).cancel(medicationId)
            return
        }

        // Для обычного срабатывания будильника, передаем работу в Foreground Service
        // Это необходимо для выполнения длительных операций (загрузка из БД) в фоне
        val serviceIntent = Intent(context, ReminderService::class.java).apply {
            putExtra(ReminderService.MEDICATION_ID_EXTRA, medicationId)
        }

        context.startForegroundService(serviceIntent)
    }
}
