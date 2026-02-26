package com.example.graduation

import android.content.Context
import android.icu.util.Calendar
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Изолированный объект-калькулятор
 * Его задача — вычислять точное время следующего будильника и форматировать его в текст для UI
 */
object NextAlarmCalculator {

    /**
     * Публичный метод для UI. Возвращает отформатированную строку о следующем приеме
     * Например: "Сегодня в 14:00", "Завтра в 09:00", "25 дек. в 12:30"
     */
    fun getNextAlarmText(medication: Medication, context: Context): String? {
        val nextAlarmCalendar = calculateNextAlarm(medication) ?: return null

        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_YEAR)
        // Клонируем, чтобы не изменять исходный 'now'
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.get(Calendar.DAY_OF_YEAR)

        val alarmDay = nextAlarmCalendar.get(Calendar.DAY_OF_YEAR)

        val dayStr = when {
            alarmDay == today && nextAlarmCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> "Сегодня"
            alarmDay == tomorrow && nextAlarmCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> "Завтра"
            else -> {
                // Для остальных случаев показываем дату, например "25 дек."
                val sdf = SimpleDateFormat("d MMM", Locale("ru"))
                sdf.format(nextAlarmCalendar.time)
            }
        }

        val timeStr = SimpleDateFormat("HH:mm", Locale("ru")).format(nextAlarmCalendar.time)

        return "$dayStr в $timeStr"
    }

    /**
     * Основной метод для AlarmScheduler. Вычисляет следующий будильник на основе правил лекарства
     * @param fromTime - Время, от которого ведется отсчет (обычно текущее)
     * @return Calendar с датой и временем следующего будильника или null, если его не будет
     */
    fun calculateNextAlarm(medication: Medication, fromTime: Long = System.currentTimeMillis()): Calendar? {
        val rule = medication.reminderRule

        // 1. Проверка базовых условий: активно ли лекарство и не закончился ли курс
        if (!medication.isActive || (rule.cycleEndDateTimestamp != null && fromTime > rule.cycleEndDateTimestamp)) {
            return null
        }

        // 2. Поиск ближайшего подходящего времени (сегодня или в будущем)
        val nextAlarmTime = findNextAlarmTime(rule, fromTime) ?: return null

        // 3. Дополнительная проверка: не выходит ли найденное время за рамки курса
        if (rule.cycleEndDateTimestamp != null && nextAlarmTime.timeInMillis > rule.cycleEndDateTimestamp) {
            return null
        }

        return nextAlarmTime
    }

    /**
     * Ищет ближайшее возможное время для будильника, начиная с `fromTime`
     */
    private fun findNextAlarmTime(rule: ReminderRule, fromTime: Long): Calendar? {
        // Сортируем время приема, чтобы искать от раннего к позднему
        val sortedTimes = rule.reminderTimes.sortedBy { it.hour * 60 + it.minute }
        val now = Calendar.getInstance().apply { timeInMillis = fromTime }

        // Сначала ищем подходящее время на сегодня
        for (time in sortedTimes) {
            val potentialAlarm = getPotentialAlarm(now, time)
            // Время должно быть в будущем и день должен соответствовать правилу
            if (potentialAlarm.timeInMillis > fromTime && isDayOfIntake(potentialAlarm, rule)) {
                return potentialAlarm // Нашли!
            }
        }

        // Если на сегодня ничего не нашлось, ищем со следующего дня
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = fromTime
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        // Перебираем до 365 дней вперед
        for (i in 0..365) {
            val nextDay = (tomorrow.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            if (isDayOfIntake(nextDay, rule)) {
                // Нашли подходящий день, берем самое раннее время в этот день
                return getPotentialAlarm(nextDay, sortedTimes.first())
            }
        }

        return null // Не нашли подходящих дней в обозримом будущем
    }

    /**
     * Проверяет, является ли указанная дата днем приема лекарства согласно правилу
     */
    private fun isDayOfIntake(date: Calendar, rule: ReminderRule): Boolean {
        return when (rule.periodicityType) {
            PeriodicityType.EVERY_DAY -> true
            PeriodicityType.WEEKLY -> {
                val dayOfWeek = date.get(Calendar.DAY_OF_WEEK)
                rule.selectedWeekDays.contains(dayOfWeek)
            }
            PeriodicityType.INTERVAL -> {
                val onDays = rule.intervalOnDays.coerceAtLeast(1)
                val offDays = rule.intervalOffDays.coerceAtLeast(1)
                val cycleLength = onDays + offDays

                val zoneId = ZoneId.systemDefault()
                val startDate = Instant.ofEpochMilli(rule.intervalStartTimestamp)
                    .atZone(zoneId)
                    .toLocalDate()
                val targetDate = LocalDate.of(
                    date.get(Calendar.YEAR),
                    date.get(Calendar.MONTH) + 1,
                    date.get(Calendar.DAY_OF_MONTH)
                )

                val diffInDays = ChronoUnit.DAYS.between(startDate, targetDate).toInt()
                val dayInCycle = Math.floorMod(diffInDays, cycleLength)
                dayInCycle < onDays
            }
        }
    }

    /**
     * Создает экземпляр Calendar для указанной даты и времени
     */
    private fun getPotentialAlarm(baseDate: Calendar, time: ReminderTime): Calendar {
        return (baseDate.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
