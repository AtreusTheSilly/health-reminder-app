package com.example.graduation

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Основной класс базы данных приложения, построенный на Room
 * Определяет сущности и предоставляет доступ к DAO
 */
@Database(entities = [Medication::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao

    companion object {
        // Аннотация @Volatile гарантирует, что значение INSTANCE всегда будет актуальным
        // для всех потоков выполнения. Это важно для синглтона
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Возвращает синглтон-экземпляр базы данных
         * Если экземпляр еще не создан, он будет инициализирован в потокобезопасном режиме
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medication_database"
                )
                    // Временная стратегия миграции. При изменении схемы база будет пересоздана
                    // Для продакшена стоит использовать более продуманную миграцию
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
