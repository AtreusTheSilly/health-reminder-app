package com.example.graduation

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson

// Модели данных для правил напоминаний

/**
 * Простое представление времени, состоящее из часов и минут.
 */
data class ReminderTime(
    val hour: Int,   // Час (0-23)
    val minute: Int  // Минута (0-59)
)

/**
 * Тип периодичности приема лекарства.
 */
enum class PeriodicityType(val displayName: String) {
    WEEKLY("Дни недели"),   // Прием по определенным дням недели.
    EVERY_DAY("Каждый день"), // Ежедневный прием.
    INTERVAL("Интервальный")  // Прием по схеме N дней прием / M дней перерыв.
}

/**
 * Описывает полное правило для создания напоминаний.
 * Эта структура хранится в базе данных в виде JSON-строки.
 */
data class ReminderRule(
    // Тип правила: ежедневно или по дням недели.
    val periodicityType: PeriodicityType = PeriodicityType.WEEKLY,
    // Множество дней недели (константы из Calendar), если тип WEEKLY.
    val selectedWeekDays: Set<Int> = emptySet(),
    // Список времени для напоминаний в течение дня.
    val reminderTimes: List<ReminderTime> = listOf(ReminderTime(9, 0)),
    // Количество дней, когда напоминания активны подряд (для INTERVAL).
    val intervalOnDays: Int = 1,
    // Количество дней перерыва подряд (для INTERVAL).
    val intervalOffDays: Int = 1,
    // Дата старта интервального цикла. Нужна для повторяемости шаблона.
    val intervalStartTimestamp: Long = System.currentTimeMillis(),
    // Дата окончания курса в виде timestamp. null означает бессрочный прием.
    val cycleEndDateTimestamp: Long? = null
)

// Конвертеры для базы данных Room

/**
 * Класс с методами для преобразования сложных типов в простые, понятные для Room.
 */
class Converters {
    private val gson = Gson()

    // Конвертеры для Uri <-> String
    @TypeConverter
    fun fromUri(value: String?): Uri? {
        return value?.let { Uri.parse(it) }
    }

    @TypeConverter
    fun uriToString(uri: Uri?): String? {
        return uri?.toString()
    }

    // Конвертеры для ReminderRule <-> String (JSON)
    @TypeConverter
    fun fromReminderRule(rule: ReminderRule?): String? {
        return gson.toJson(rule)
    }

    @TypeConverter
    fun toReminderRule(json: String?): ReminderRule? {
        return gson.fromJson(json, ReminderRule::class.java)
    }
}

// Основная сущность для хранения в базе данных

/**
 * Представляет одно лекарство со всеми его свойствами.
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String,
    val photoUri: Uri?,
    // Сложное правило напоминания, хранится как JSON.
    val reminderRule: ReminderRule,
    // Флаг, показывающий, активно ли напоминание. Можно приостановить прием.
    val isActive: Boolean = true
)
