package com.example.graduation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Срабатывает после перезагрузки устройства.
 * Должен запускать фоновую задачу для перепланировки всех будильников,
 * так как системные будильники сбрасываются при выключении телефона
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Проверяем, что событие — это именно завершение загрузки системы
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Создаем и запускаем одноразовую задачу для MedicationCheckWorker
            // Этот воркер проверит все лекарства и установит для них будильники заново
            val workRequest = OneTimeWorkRequestBuilder<MedicationCheckWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
