// MainActivity.kt
package com.example.graduation

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.graduation.ui.theme.GraduationTheme
import java.sql.Timestamp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GraduationTheme {
                val context = LocalContext.current
                val application = context.applicationContext as MedicationApplication
                val appViewModel: AddMedicationViewModel = viewModel(
                    factory = ViewModelFactory(application.database.medicationDao(), context)
                )
                MedicationTrackerApp(
                    appViewModel = appViewModel,
                    activity = this
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
                    startActivity(it)
                }
            }
        }
    }

}

// Главный навигатор приложения
@SuppressLint("ContextCastToActivity")
@Composable
fun MedicationTrackerApp(
    appViewModel: AddMedicationViewModel,
    activity: ComponentActivity
) {
    val medications by appViewModel.medications.collectAsState()
    var currentScreen by remember { mutableStateOf("List") }

    fun navigateToList() {
        appViewModel.clearInputFields()
        currentScreen = "List"
    }

    // Создание новой записи
    fun navigateToAdd() {
        appViewModel.clearInputFields()
        currentScreen = "Add"
    }

    // Редактироване существующей записи
    fun navigateToEdit(medication: Medication) {
        appViewModel.loadMedicationForEditing(medication)
        currentScreen = "Add"
    }

    when (currentScreen) {
        "List" -> MedicationListScreen(
            appViewModel = appViewModel,
            medications = medications,
            onAddClick = { navigateToAdd() },
            onMedicationClick = { medication -> navigateToEdit(medication) },
            onExitApp = {
                activity.finish()
            }
        )
        "Add" -> AddMedicationScreen(
            addMedicationViewModel = appViewModel,
            onNavigateBack = { navigateToList() }
        )
    }
}

// Экран списка с обработчиком "Назад" для выхода
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    appViewModel: AddMedicationViewModel,
    medications: List<Medication>,
    onAddClick: () -> Unit,
    onMedicationClick: (Medication) -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var backPressTime by remember { mutableLongStateOf(0L) }
    val sortedMedications = remember(medications) {
        medications.sortedWith(compareBy<Medication> { !it.isActive }.thenBy {
            NextAlarmCalculator.calculateNextAlarm(it)?.timeInMillis ?: Long.MAX_VALUE
        }.thenBy { it.name.lowercase() })
    }

    // Обработчик двойного нажатия "назад" для выхода
    BackHandler(enabled = true) {
        if (appViewModel.isSelectionMode) {
            appViewModel.clearSelection()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressTime > 2000) {
                backPressTime = currentTime
                Toast.makeText(context, "Нажмите \"Назад\" ещё раз, чтобы выйти", Toast.LENGTH_SHORT).show()
            } else {
                onExitApp()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (appViewModel.isSelectionMode) {
                // Верхняя панель в режиме выбора
                SelectionTopAppBar(
                    selectedCount = appViewModel.selectedMedicationIds.size,
                    onClearSelection = { appViewModel.clearSelection() },
                    onDeleteSelected = { appViewModel.deleteSelectedMedications() }
                )
            } else {
                // Стандартная верхняя панель
                TopAppBar(
                    title = { Text("Мои лекарства") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        floatingActionButton = {
            // Прячем кнопку, если активен режим выбора
            if (!appViewModel.isSelectionMode) {
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить лекарство")
                }
            }
        }
    ) { innerPadding ->
        if (sortedMedications.isEmpty()) {
            // Отображение меню, если записей нет
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Пока нет лекарств",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Добавьте первое напоминание, чтобы начать отслеживание приёма",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Добавить лекарство")
                    }
                }
            }
        } else LazyColumn(
            // Отображение главного меню со списком лекарств
            contentPadding = innerPadding,
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sortedMedications, key = { it.id }) { medication ->
                val isSelected = appViewModel.selectedMedicationIds.contains(medication.id)
                MedicationListItem(
                    medication = medication,
                    isSelected = isSelected,
                    onItemClick = {
                        if (appViewModel.isSelectionMode) {
                            appViewModel.toggleMedicationSelection(medication.id)
                        } else {
                            onMedicationClick(medication)
                        }
                    },
                    onItemLongClick = {
                        if (!appViewModel.isSelectionMode) {
                            appViewModel.enterSelectionMode()
                            appViewModel.toggleMedicationSelection(medication.id)
                        }
                    }
                )
            }
        }
    }
}

// Верхня панель при выборе записей
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount выбрано") },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = "Отменить выбор")
            }
        },
        actions = {
            IconButton(onClick = onDeleteSelected) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}

// Элемент списка
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MedicationListItem(
    medication: Medication,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        // изменение отображения в зависимости от того, активны ли напоминания для конкретной записи
        elevation = if (medication.isActive) CardDefaults.cardElevation(defaultElevation = 4.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                !medication.isActive -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val alpha = if (medication.isActive) 1f else 0.5f
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .graphicsLayer(alpha = alpha),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    // Показываем галочку, если выбрано
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Выбрано",
                        modifier = Modifier.fillMaxSize(0.7f),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else if (medication.photoUri != null) {
                    // Показываем фото лекарства
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(medication.photoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = medication.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                    )
                } else {
                    // Показываем заглушку, если фото лекарства отсутствует
                    Icon(
                        imageVector = Icons.Default.Vaccines,
                        contentDescription = "Иконка лекарства",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f), // Занимает оставшееся место
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (medication.isActive) "Активно" else "Приостановлено",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (medication.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                val context = LocalContext.current
                // Получаем и отображаем время следующего уведомления
                val nextAlarmText = NextAlarmCalculator.getNextAlarmText(medication, context)
                if (nextAlarmText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = nextAlarmText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// Экран добавления/редактирования с диалогом о несохраненных изменениях
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationScreen(
    modifier: Modifier = Modifier,
    addMedicationViewModel: AddMedicationViewModel,
    onNavigateBack: () -> Unit
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> addMedicationViewModel.onPhotoSelected(uri) }
    )
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap -> addMedicationViewModel.onPhotoCaptured(bitmap) }
    )

    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var showValidationErrors by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    // Обработчик кнопки "назад" на экране редактирования
    BackHandler(enabled = true) {
        if (addMedicationViewModel.hasUnsavedChanges()) {
            showUnsavedChangesDialog = true
        } else {
            onNavigateBack() // Если изменений нет, просто возвращаемся назад
        }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text("Источник фото") },
            text = { Text("Выберите источник изображения") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoSourceDialog = false
                    takePhotoLauncher.launch(null)
                }) {
                    Text("Сделать фото")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoSourceDialog = false
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Text("Выбрать из галереи")
                }
            }
        )
    }

    if (showUnsavedChangesDialog) {
        UnsavedChangesDialog(
            onDismiss = { showUnsavedChangesDialog = false }, // Просто закрыть диалог
            onConfirmSave = {
                val isSaved = addMedicationViewModel.saveMedication()
                if (isSaved) {
                    showValidationErrors = false
                    onNavigateBack() // Сохраняем и возвращаемся
                } else {
                    showUnsavedChangesDialog = false
                    showValidationErrors = true
                }
            },
            onConfirmDiscard = {
                onNavigateBack() // Не сохраняем и возвращаемся
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (addMedicationViewModel.isEditing) "Редактировать" else "Добавить лекарство") },
                navigationIcon = {
                    // Кнопка "Назад" в AppBar работает так же, как системная
                    IconButton(onClick = {
                        if (addMedicationViewModel.hasUnsavedChanges()) {
                            showUnsavedChangesDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { // Дизайн окна добавления/редактирования записи
        innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PhotoPicker(
                photoUri = addMedicationViewModel.medicationPhotoUri,
                onPhotoClick = {
                    showPhotoSourceDialog = true
                },
                modifier = Modifier.padding(bottom = 10.dp)
            )
            OutlinedTextField(
                value = addMedicationViewModel.medicationName,
                onValueChange = {
                    addMedicationViewModel.onNameChange(it)
                    if (it.isNotBlank()) showValidationErrors = false
                },
                label = { Text("Название лекарства") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = showValidationErrors && addMedicationViewModel.medicationName.isBlank(),
                supportingText = {
                    if (showValidationErrors && addMedicationViewModel.medicationName.isBlank()) {
                        Text("Введите название лекарства")
                    }
                }
            )
            OutlinedTextField(
                value = addMedicationViewModel.medicationDescription,
                onValueChange = { addMedicationViewModel.onDescriptionChange(it) },
                label = { Text("Заметки (например, дозировка)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(2.dp))

            // 1. Переключатель режима периодичности
            PeriodicitySelector(
                selectedType = addMedicationViewModel.reminderRule.periodicityType,
                onTypeSelected = { addMedicationViewModel.updatePeriodicityType(it) },
                // Передаем параметры для селектора дней недели, так как он теперь внутри PeriodicitySelector
                selectedWeekDays = addMedicationViewModel.reminderRule.selectedWeekDays,
                onDayOfWeekToggle = { addMedicationViewModel.toggleWeekDay(it) },
                intervalOnDaysInput = addMedicationViewModel.intervalOnDaysInput,
                intervalOffDaysInput = addMedicationViewModel.intervalOffDaysInput,
                onIntervalOnDaysChange = { addMedicationViewModel.updateIntervalOnDaysInput(it) },
                onIntervalOffDaysChange = { addMedicationViewModel.updateIntervalOffDaysInput(it) },
                isIntervalOnDaysError = showValidationErrors &&
                        addMedicationViewModel.reminderRule.periodicityType == PeriodicityType.INTERVAL &&
                        addMedicationViewModel.intervalOnDaysInput.isBlank(),
                isIntervalOffDaysError = showValidationErrors &&
                        addMedicationViewModel.reminderRule.periodicityType == PeriodicityType.INTERVAL &&
                        addMedicationViewModel.intervalOffDaysInput.isBlank()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Список времени напоминаний
            addMedicationViewModel.reminderRule.reminderTimes.forEachIndexed { index, time ->
                TimeSelector(
                    time = time,
                    onTimeSelected = { hour, minute ->
                        addMedicationViewModel.updateReminderTime(index, hour, minute)
                    },
                    onRemove = { addMedicationViewModel.removeReminderTime(index) },
                    isRemovable = addMedicationViewModel.reminderRule.reminderTimes.size > 1
                )
            }

            // Кнопка "Добавить время"
            TextButton(
                onClick = { addMedicationViewModel.addReminderTime() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить время")
                Spacer(Modifier.width(8.dp))
                Text("Добавить время приёма")
            }

            HorizontalDivider()

            // Выбор даты окончания
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Бессрочный приём")
                Switch(
                    checked = addMedicationViewModel.reminderRule.cycleEndDateTimestamp == null,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            addMedicationViewModel.updateEndDate(null) // null для бессрочного
                        } else {
                            // устанавливаем дату по умолчанию
                            val calendar = Calendar.getInstance()
                            calendar.add(Calendar.MONTH, 1)
                            addMedicationViewModel.updateEndDate(calendar.timeInMillis)
                        }
                    }
                )
            }

            if (addMedicationViewModel.reminderRule.cycleEndDateTimestamp != null) {
                EndDatePicker(
                    selectedDateTimestamp = addMedicationViewModel.reminderRule.cycleEndDateTimestamp,
                    onDateSelected = { addMedicationViewModel.updateEndDate(it) }
                )
            }

            Spacer(Modifier.weight(1f)) // Заполнитель, чтобы кнопка была внизу
            Button(
                onClick = {
                    val isSaved = addMedicationViewModel.saveMedication()
                    if (isSaved) {
                        showValidationErrors = false
                        onNavigateBack()
                    } else {
                        showValidationErrors = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Сохранить")
            }
        }
    }
}

// Выбор фото для записи
@Composable
fun PhotoPicker(photoUri: Uri?, onPhotoClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onPhotoClick),
        contentAlignment = Alignment.Center
    ) {
        if (photoUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Фото лекарства",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = "Добавить фото",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// Диалог о несохранённых изменениях
@Composable
fun UnsavedChangesDialog(
    onDismiss: () -> Unit,
    onConfirmSave: () -> Unit,
    onConfirmDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Несохраненные изменения") },
        text = { Text("Вы хотите сохранить внесенные изменения?") },
        confirmButton = {
            Button(onClick = onConfirmSave) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onConfirmDiscard) {
                Text("Не сохранять")
            }
        },
    )
}


/**
 * Форматирует время для отображения, например, 9:0 в "09:00"
 */
private fun formatTime(hour: Int, minute: Int): String {
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

/**
 * Форматирует дату для отображения
 */
private fun formatDate(timestamp: Long?): String {
    if (timestamp == null) return "Бессрочно"
    val sdf = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("ru"))
    return sdf.format(java.util.Date(timestamp))
}

// Компонент для выбора времени
@Composable
fun TimeSelector(
    time: ReminderTime,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onRemove: () -> Unit,
    isRemovable: Boolean
) {
    val context = LocalContext.current
    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, hour: Int, minute: Int -> onTimeSelected(hour, minute) },
        time.hour, time.minute, true
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Время приёма:",
            style = MaterialTheme.typography.bodyLarge
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { timePickerDialog.show() }) {
                Text(formatTime(time.hour, time.minute), fontSize = 18.sp)
            }
            if (isRemovable) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.RemoveCircleOutline, "Удалить время")
                }
            }
        }
    }
}

// Переключатель режима напоминаний
@Composable
fun PeriodicitySelector(
    selectedType: PeriodicityType,
    onTypeSelected: (PeriodicityType) -> Unit,
    selectedWeekDays: Set<Int>,
    onDayOfWeekToggle: (Int) -> Unit,
    intervalOnDaysInput: String,
    intervalOffDaysInput: String,
    onIntervalOnDaysChange: (String) -> Unit,
    onIntervalOffDaysChange: (String) -> Unit,
    isIntervalOnDaysError: Boolean,
    isIntervalOffDaysError: Boolean
) {
    Column {
        Text("Периодичность", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // Кнопка "Каждый день"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTypeSelected(PeriodicityType.EVERY_DAY) }
                .padding(vertical = 4.dp)
        ) {
            RadioButton(
                selected = selectedType == PeriodicityType.EVERY_DAY,
                onClick = { onTypeSelected(PeriodicityType.EVERY_DAY) }
            )
            Spacer(Modifier.width(8.dp))
            Text("Каждый день")
        }

        // Кнопка "Дни недели"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTypeSelected(PeriodicityType.WEEKLY) }
                .padding(vertical = 4.dp)
        ) {
            RadioButton(
                selected = selectedType == PeriodicityType.WEEKLY,
                onClick = { onTypeSelected(PeriodicityType.WEEKLY) }
            )
            Spacer(Modifier.width(8.dp))
            Text("Дни недели")
        }

        // Кнопка "Интервальный"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTypeSelected(PeriodicityType.INTERVAL) }
                .padding(vertical = 4.dp)
        ) {
            RadioButton(
                selected = selectedType == PeriodicityType.INTERVAL,
                onClick = { onTypeSelected(PeriodicityType.INTERVAL) }
            )
            Spacer(Modifier.width(8.dp))
            Text("Интервальный")
        }

        // Показываем дополнительные настройки для выбранного режима
        when (selectedType) {
            PeriodicityType.WEEKLY -> {
                Spacer(Modifier.height(16.dp))
                WeekDaySelector(
                    selectedDays = selectedWeekDays,
                    onDayToggle = onDayOfWeekToggle
                )
            }

            PeriodicityType.INTERVAL -> {
                Spacer(Modifier.height(12.dp))
                IntervalSelector(
                    onDaysInput = intervalOnDaysInput,
                    offDaysInput = intervalOffDaysInput,
                    onOnDaysChange = onIntervalOnDaysChange,
                    onOffDaysChange = onIntervalOffDaysChange,
                    isOnDaysError = isIntervalOnDaysError,
                    isOffDaysError = isIntervalOffDaysError
                )
            }

            PeriodicityType.EVERY_DAY -> Unit
        }
    }
}

@Composable
private fun IntervalSelector(
    onDaysInput: String,
    offDaysInput: String,
    onOnDaysChange: (String) -> Unit,
    onOffDaysChange: (String) -> Unit,
    isOnDaysError: Boolean,
    isOffDaysError: Boolean
) {
    OutlinedTextField(
        value = onDaysInput,
        onValueChange = { value -> onOnDaysChange(value.filter { it.isDigit() }) },
        label = { Text("Дней с напоминаниями") },
        placeholder = { Text("Например, 3") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = isOnDaysError,
        supportingText = {
            if (isOnDaysError) {
                Text("Заполните поле")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = offDaysInput,
        onValueChange = { value -> onOffDaysChange(value.filter { it.isDigit() }) },
        label = { Text("Дней без напоминаний") },
        placeholder = { Text("Например, 2") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = isOffDaysError,
        supportingText = {
            if (isOffDaysError) {
                Text("Заполните поле")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    val onDays = onDaysInput.toIntOrNull()
    val offDays = offDaysInput.toIntOrNull()
    val cycleText = if (onDays != null && offDays != null && onDays > 0 && offDays > 0) {
        "Цикл: $onDays дн. с напоминаниями / $offDays дн. перерыв"
    } else {
        "Введите параметры интервального цикла"
    }

    Text(
        text = cycleText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

// Выбор дней недели
@Composable
fun WeekDaySelector(
    selectedDays: Set<Int>,
    onDayToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Определяем порядок и названия дней недели
    val weekDays = listOf(
        "ПН" to java.util.Calendar.MONDAY,
        "ВТ" to java.util.Calendar.TUESDAY,
        "СР" to java.util.Calendar.WEDNESDAY,
        "ЧТ" to java.util.Calendar.THURSDAY,
        "ПТ" to java.util.Calendar.FRIDAY,
        "СБ" to java.util.Calendar.SATURDAY,
        "ВС" to java.util.Calendar.SUNDAY
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        weekDays.forEach { (dayName, dayConstant) ->
            val isSelected = selectedDays.contains(dayConstant)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onDayToggle(dayConstant) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayName,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Компонент для выбора даты окончания приёма
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndDatePicker(
    selectedDateTimestamp: Long?,
    onDateSelected: (Long?) -> Unit
) {
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateSelected(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Закончить приём:", style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { showDatePicker = true }) {
            Text(formatDate(selectedDateTimestamp), fontSize = 18.sp)
        }
    }
}