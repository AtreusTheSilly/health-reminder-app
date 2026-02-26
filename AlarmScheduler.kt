// AlarmScheduler.kt
package com.example.graduation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Отвечает за установку и отмену системных будильников для напоминаний
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Планирует следующий ближайший будильник для указанного лекарства
     * Если курс приема окончен или нет подходящих дат, отменяет существующие будильники
     */
    fun schedule(medication: Medication, testTimeOffsetInMillis: Long = 0) {
        if (!canScheduleExactAlarms()) return

        // Рассчитываем время следующего напоминания
        val nextAlarmTime = NextAlarmCalculator.calculateNextAlarm(medication, System.currentTimeMillis() + testTimeOffsetInMillis)

        if (nextAlarmTime == null) {
            // Если время не определено, значит, курс завершен
            // На всякий случай отменяем все связанные с этим лекарством будильники
            cancel(medication)
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("MEDICATION_ID", medication.id)
        }

        // Код запроса должен быть уникальным для каждого лекарства
        // Это позволяет обновлять или отменять будильник, не затрагивая другие
        val requestCode = medication.id

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextAlarmTime.timeInMillis,
            pendingIntent
        )
    }

    /**
     * Устанавливает одноразовый будильник через 10 минут от текущего времени
     * Используется для функции "Отложить" в уведомлении
     */
    fun scheduleSnooze(medicationId: Int) {
        if (!canScheduleExactAlarms()) return

        val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000 // 10 минут

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("MEDICATION_ID", medicationId)
        }

        // Чтобы отложенный будильник не конфликтовал с основным,
        // для него используется уникальный код запроса (отрицательный ID лекарства)
        val snoozeRequestCode = -medicationId

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            snoozeTime,
            pendingIntent
        )
    }

    /**
     * Отменяет все будильники (основной и отложенный) для лекарства
     */
    fun cancel(medication: Medication) {
        cancelByRequestCode(medication.id)
        cancelByRequestCode(-medication.id) // Отменяем и возможный отложенный будильник
    }

    private fun cancelByRequestCode(requestCode: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            // FLAG_NO_CREATE важен, чтобы не создавать новый интент, а найти существующий
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return // Если интента нет, то и отменять нечего

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Проверяет, есть ли у приложения разрешение на установку точных будильников
     * На Android 12 (S) и выше это разрешение должен выдавать пользователь
     */
    private fun canScheduleExactAlarms(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // На старых версиях разрешение есть по умолчанию
        }
    }
}
