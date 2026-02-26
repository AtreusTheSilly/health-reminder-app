package com.example.graduation

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground Service, который выполняет тяжелую работу после срабатывания будильника
 * Его задача: загрузить данные из БД, показать уведомление и перепланировать следующий будильник
 * Использование сервиса переднего плана гарантирует, что система не убьет процесс
 */
class ReminderService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        const val MEDICATION_ID_EXTRA = "MEDICATION_ID_EXTRA"
        private const val FOREGROUND_NOTIFICATION_ID = -1 // ID для служебного уведомления
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val medicationId = intent?.getIntExtra(MEDICATION_ID_EXTRA, -1) ?: -1

        if (medicationId == -1) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Необходимо как можно скорее вызвать startForeground
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())

        scope.launch {
            try {
                val database = AppDatabase.getDatabase(applicationContext)
                val medication = database.medicationDao().getMedicationById(medicationId)

                if (medication != null) {
                    // Показываем основное, видимое пользователю уведомление
                    val notificationHelper = NotificationHelper(applicationContext)
                    notificationHelper.showNotification(medication)

                    // Запускаем воркер для перепланировки следующего будильника
                    val workManager = WorkManager.getInstance(applicationContext)
                    val workRequest = OneTimeWorkRequestBuilder<MedicationCheckWorker>().build()
                    workManager.enqueue(workRequest)
                }
            } finally {
                // Убираем служебное уведомление и останавливаем сервис
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Создает минимальное уведомление, необходимое для запуска foreground service
     * Оно почти невидимо для пользователя
     */
    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, NotificationChannels.REMINDER_CHANNEL_ID)
            .setContentTitle("Проверка напоминаний...")
            .setSmallIcon(R.drawable.ic_stat_medication)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Привязка к сервису не используется
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Отменяем все корутины, когда сервис уничтожается
        job.cancel()
    }
}
