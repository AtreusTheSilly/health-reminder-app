package com.example.graduation

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) для работы с таблицей лекарств.
 * Определяет методы для взаимодействия с базой данных.
 */
@Dao
interface MedicationDao {

    /**
     * Вставляет или обновляет лекарство в таблице.
     * Если запись с таким `id` уже существует, она будет обновлена.
     * Если нет — будет создана новая.
     */
    @Upsert
    suspend fun upsertMedication(medication: Medication): Long

    /**
     * Возвращает поток (Flow) со списком всех лекарств, отсортированных по имени.
     * Flow автоматически эмитит новый список при любом изменении в таблице,
     * что позволяет UI обновляться реактивно.
     */
    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAllMedications(): Flow<List<Medication>>

    /**
     * Получает одно лекарство по его уникальному ID.
     * Это suspend-функция, так как она выполняет разовый запрос к БД.
     */
    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun getMedicationById(id: Int): Medication?

    /**
     * Удаляет из таблицы лекарства по списку их ID.
     */
    @Query("DELETE FROM medications WHERE id IN (:ids)")
    suspend fun deleteMedicationsByIds(ids: List<Int>)

    /**
     * Получает весь список лекарств в виде suspend-функции.
     * Удобно для фоновых задач, которым не нужна реактивность Flow.
     */
    @Query("SELECT * FROM medications")
    suspend fun getAllMedicationsSuspend(): List<Medication>

    /**
     * Обновляет статус активности (isActive) для конкретного лекарства.
     */
    @Query("UPDATE medications SET isActive = :isActive WHERE id = :id")
    suspend fun updateMedicationStatus(id: Int, isActive: Boolean)
}
