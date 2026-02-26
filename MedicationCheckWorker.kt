package com.example.graduation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Фоновая задача (Worker), отвечающая за регулярную проверку и обновление состояния будильников.
 * Запускается периодически, а также после перезагрузки устройства или сохранения лекарства.
 */
class MedicationCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val scheduler = AlarmScheduler(applicationContext)
        val medications = database.medicationDao().getAllMedicationsSuspend()

        medications.forEach { med ->
            // Пропускаем невалидные записи, хотя таких быть не должно.
            if (med.id <= 0) {
                return@forEach
            }

            val rule = med.reminderRule
            // Проверяем, не закончился ли курс приема.
            if (rule.cycleEndDateTimestamp != null && System.currentTimeMillis() > rule.cycleEndDateTimestamp) {
                // Если курс закончился, а лекарство все еще активно, деактивируем его.
                if (med.isActive) {
                    database.medicationDao().updateMedicationStatus(med.id, false)
                }
                // Отменяем все связанные будильники.
                scheduler.cancel(med)
                return@forEach // Переходим к следующему лекарству.
            }

            // Если курс продолжается, смотрим на статус лекарства.
            if (med.isActive) {
                // Для активных лекарств — планируем (или перепланируем) следующий будильник.
                scheduler.schedule(med)
            } else {
                // Для неактивных (приостановленных) — отменяем будильники, чтобы они не срабатывали.
                scheduler.cancel(med)
            }
        }
        return Result.success()
    }
}
