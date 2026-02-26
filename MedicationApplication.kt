package com.example.graduation

import android.app.Application
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Кастомный класс Application для инициализации компонентов, которые должны жить
 * на протяжении всего жизненного цикла приложения.
 */
class MedicationApplication : Application() {
    // Ленивая инициализация базы данных. Экземпляр будет создан только при первом обращении.
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // Создаем каналы уведомлений при старте приложения.
        NotificationChannels.create(this)
        // Настраиваем и запускаем периодические фоновые задачи.
        setupRecurringWork()
    }

    private fun setupRecurringWork() {
        // Эта задача будет запускаться примерно раз в 6 часов.
        // Она нужна для регулярной проверки и перепланировки будильников.
        val repeatingRequest = PeriodicWorkRequestBuilder<MedicationCheckWorker>(6, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "MedicationCheckWork", // Уникальное имя для задачи
            ExistingPeriodicWorkPolicy.KEEP, // Если задача уже запланирована, ничего не делать
            repeatingRequest
        )
    }
}
