package com.example.graduation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Фабрика для создания экземпляров ViewModel.
 * Необходима, так как AddMedicationViewModel имеет конструктор с параметрами (dao, context),
 * и стандартная фабрика не смогла бы его создать
 */
class ViewModelFactory(private val dao: MedicationDao, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Проверяем, можем ли мы создать запрашиваемый тип ViewModel
        if (modelClass.isAssignableFrom(AddMedicationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Создаем и возвращаем экземпляр, передавая в него зависимости
            return AddMedicationViewModel(dao, context) as T
        }
        // Если мы не знаем, как создать такой ViewModel, выбрасываем исключение
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
