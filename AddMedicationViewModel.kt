// AddMedicationViewModel.kt
package com.example.graduation

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Управляет состоянием экранов добавления, редактирования и списка лекарств
 * Обрабатывает логику сохранения, удаления, а также временные изменения в формах
 */
class AddMedicationViewModel(private val dao: MedicationDao, private val context: Context) : ViewModel() {

    // Поток со списком всех лекарств из базы данных
    val medications: StateFlow<List<Medication>> = dao.getAllMedications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Состояние режима выбора лекарств в списке
    var isSelectionMode by mutableStateOf(false)
        private set
    val selectedMedicationIds = mutableStateListOf<Int>()

    // Поля для формы добавления/редактирования лекарства
    var medicationName by mutableStateOf("")
        private set
    var medicationDescription by mutableStateOf("")
        private set
    var medicationPhotoUri by mutableStateOf<Uri?>(null)
        private set
    var reminderRule by mutableStateOf(ReminderRule())
        private set
    var intervalOnDaysInput by mutableStateOf("")
        private set
    var intervalOffDaysInput by mutableStateOf("")
        private set

    // ID лекарства, которое сейчас редактируется. null, если создается новое.
    private var editingMedicationId by mutableStateOf<Int?>(null)
    private var originalState: OriginalMedicationState? = null
    val isEditing: Boolean get() = editingMedicationId != null

    private val alarmScheduler = AlarmScheduler(context)

    // Хранит исходное состояние лекарства для проверки на наличие несохраненных изменений.
    private data class OriginalMedicationState(
        val name: String,
        val description: String,
        val photoUri: Uri?,
        val reminderRule: ReminderRule,
        val intervalOnDaysInput: String,
        val intervalOffDaysInput: String
    )

    /**
     * Проверяет, есть ли несохраненные изменения в форме.
     * Помогает предотвратить случайную потерю данных при выходе с экрана.
     */
    fun hasUnsavedChanges(): Boolean {
        if (!isEditing) {
            // Для нового лекарства проверяем, заполнено ли хотя бы одно поле.
            return medicationName.isNotBlank() || medicationDescription.isNotBlank() || medicationPhotoUri != null ||
                    intervalOnDaysInput.isNotBlank() || intervalOffDaysInput.isNotBlank()
        }
        // Для существующего лекарства сравниваем текущие данные с исходными.
        return originalState?.name != medicationName ||
                originalState?.description != medicationDescription ||
                originalState?.photoUri != medicationPhotoUri ||
                originalState?.reminderRule != reminderRule ||
                originalState?.intervalOnDaysInput != intervalOnDaysInput ||
                originalState?.intervalOffDaysInput != intervalOffDaysInput
    }

    // Управление режимом выбора в списке

    fun enterSelectionMode() {
        isSelectionMode = true
    }

    fun toggleMedicationSelection(medicationId: Int) {
        if (selectedMedicationIds.contains(medicationId)) {
            selectedMedicationIds.remove(medicationId)
        } else {
            selectedMedicationIds.add(medicationId)
        }
        // Если пользователь отменил выбор последнего элемента, режим выбора отключается.
        if (selectedMedicationIds.isEmpty()) {
            isSelectionMode = false
        }
    }

    fun clearSelection() {
        selectedMedicationIds.clear()
        isSelectionMode = false
    }

    fun deleteSelectedMedications() {
        viewModelScope.launch {
            // Перед удалением из БД, избавляемся от связанных фото и отменяем будильники.
            selectedMedicationIds.forEach { id ->
                val medication = medications.value.firstOrNull { it.id == id }
                medication?.photoUri?.path?.let { path ->
                    File(path).delete()
                }
                if (medication != null) alarmScheduler.cancel(medication)
            }
            dao.deleteMedicationsByIds(selectedMedicationIds.toList())
            clearSelection()
        }
    }

    // Обработчики изменений в полях формы

    fun onNameChange(newName: String) { medicationName = newName }
    fun onDescriptionChange(newDescription: String) { medicationDescription = newDescription }

    fun onPhotoSelected(uri: Uri?) {
        if (uri == null) {
            medicationPhotoUri = null
            return
        }
        // Чтобы сохранить доступ к фото, копируем его во внутреннее хранилище приложения
        viewModelScope.launch {
            val internalUri = copyPhotoToInternalStorage(uri)
            medicationPhotoUri = internalUri
        }
    }

    fun onPhotoCaptured(photoBitmap: Bitmap?) {
        if (photoBitmap == null) return

        viewModelScope.launch {
            val internalUri = saveBitmapToInternalStorage(photoBitmap)
            medicationPhotoUri = internalUri
        }
    }

    fun updatePeriodicityType(newType: PeriodicityType) {
        val previousType = reminderRule.periodicityType

        reminderRule = reminderRule.copy(
            periodicityType = newType,
            intervalStartTimestamp = if (newType == PeriodicityType.INTERVAL && previousType != PeriodicityType.INTERVAL) {
                System.currentTimeMillis()
            } else {
                reminderRule.intervalStartTimestamp
            }
        )
    }

    fun updateIntervalOnDaysInput(input: String) {
        intervalOnDaysInput = input
        input.toIntOrNull()?.takeIf { it > 0 }?.let { days ->
            reminderRule = reminderRule.copy(intervalOnDays = days)
        }
    }

    fun updateIntervalOffDaysInput(input: String) {
        intervalOffDaysInput = input
        input.toIntOrNull()?.takeIf { it > 0 }?.let { days ->
            reminderRule = reminderRule.copy(intervalOffDays = days)
        }
    }

    /**
     * Добавляет или убирает день недели из списка приема.
     * @param day Константа из `Calendar`, например, `Calendar.MONDAY`
     */
    fun toggleWeekDay(day: Int) {
        val currentDays = reminderRule.selectedWeekDays.toMutableSet()
        if (currentDays.contains(day)) {
            currentDays.remove(day)
        } else {
            currentDays.add(day)
        }

        // Нельзя оставить 0 дней в режиме "Дни недели"
        // Если пользователь снимает последний день, изменение не применяется
        if (currentDays.isNotEmpty()) {
            reminderRule = reminderRule.copy(selectedWeekDays = currentDays)
        }
    }

    fun addReminderTime() {
        val newTimes = reminderRule.reminderTimes.toMutableList()
        newTimes.add(ReminderTime(12, 0)) // Новое время по умолчанию (полдень)
        reminderRule = reminderRule.copy(reminderTimes = newTimes)
    }

    fun removeReminderTime(index: Int) {
        // Не позволяем удалить последнее время напоминания
        if (reminderRule.reminderTimes.size > 1) {
            val newTimes = reminderRule.reminderTimes.toMutableList()
            newTimes.removeAt(index)
            reminderRule = reminderRule.copy(reminderTimes = newTimes)
        }
    }

    fun updateReminderTime(index: Int, hour: Int, minute: Int) {
        val newTimes = reminderRule.reminderTimes.toMutableList()
        newTimes[index] = ReminderTime(hour, minute)
        reminderRule = reminderRule.copy(reminderTimes = newTimes)
    }

    fun updateEndDate(dateTimestamp: Long?) {
        reminderRule = reminderRule.copy(cycleEndDateTimestamp = dateTimestamp)
    }

    /**
     * Заполняет поля формы данными из существующего лекарства для редактирования
     * Также сохраняет исходное состояние для отслеживания изменений
     */
    fun loadMedicationForEditing(medication: Medication) {
        editingMedicationId = medication.id
        medicationName = medication.name
        medicationDescription = medication.description
        medicationPhotoUri = medication.photoUri
        reminderRule = medication.reminderRule

        intervalOnDaysInput = medication.reminderRule.intervalOnDays.toString()
        intervalOffDaysInput = medication.reminderRule.intervalOffDays.toString()

        originalState = OriginalMedicationState(
            name = medication.name,
            description = medication.description,
            photoUri = medication.photoUri,
            reminderRule = medication.reminderRule,
            intervalOnDaysInput = intervalOnDaysInput,
            intervalOffDaysInput = intervalOffDaysInput
        )
    }

    /**
     * Сохраняет новое или обновляет существующее лекарство в базе данных
     */
    fun saveMedication(): Boolean {
        if (medicationName.isBlank()) return false

        val validatedRule = if (reminderRule.periodicityType == PeriodicityType.INTERVAL) {
            val onDays = intervalOnDaysInput.toIntOrNull()?.takeIf { it > 0 } ?: return false
            val offDays = intervalOffDaysInput.toIntOrNull()?.takeIf { it > 0 } ?: return false
            reminderRule.copy(intervalOnDays = onDays, intervalOffDays = offDays)
        } else {
            reminderRule
        }

        viewModelScope.launch {
            val medicationToSave = Medication(
                id = editingMedicationId ?: 0,
                name = medicationName,
                description = medicationDescription,
                photoUri = medicationPhotoUri,
                reminderRule = validatedRule,
                isActive = true // При любом сохранении лекарство становится активным
            )

            dao.upsertMedication(medicationToSave)
            // Запускаем воркер для перепланировки будильников после сохранения
            val workRequest = OneTimeWorkRequestBuilder<MedicationCheckWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
        clearInputFields()
        return true
    }

    /**
     * Сбрасывает все поля формы к их значениям по умолчанию
     * Вызывается после сохранения или при отмене редактирования
     */
    fun clearInputFields() {
        medicationName = ""
        medicationDescription = ""
        medicationPhotoUri = null
        reminderRule = ReminderRule()
        intervalOnDaysInput = ""
        intervalOffDaysInput = ""
        editingMedicationId = null
        originalState = null // Сбрасываем сохраненное состояние
    }

    /**
     * Копирует выбранное изображение в приватное хранилище приложения
     * @return Uri нового файла или null в случае ошибки
     */
    private suspend fun saveBitmapToInternalStorage(bitmap: Bitmap): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val imagesDir = File(context.filesDir, "images")
                if (!imagesDir.exists()) {
                    imagesDir.mkdir()
                }
                val file = File(imagesDir, "med_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
                file.toUri()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun copyPhotoToInternalStorage(uri: Uri): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val imagesDir = File(context.filesDir, "images")
                if (!imagesDir.exists()) {
                    imagesDir.mkdir()
                }
                val file = File(imagesDir, "med_${System.currentTimeMillis()}.jpg")

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                file.toUri()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}