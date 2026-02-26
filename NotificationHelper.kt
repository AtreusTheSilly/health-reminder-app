package com.example.graduation

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation

/**
 * Помощник для создания и отображения уведомлений о приеме лекарств
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Создает и показывает уведомление для указанного лекарства
     */
    suspend fun showNotification(medication: Medication) {
        // Загружаем фото лекарства для отображения в уведомлении
        val largeIcon = getNotificationIcon(medication.photoUri)

        // Интент для кнопки "Отложить"
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.SNOOZE_ACTION
            putExtra("MEDICATION_ID", medication.id)
        }

        // RequestCode для отложенного будильника должен быть уникальным,
        // чтобы не перезаписывать основной будильник лекарства
        val snoozeRequestCode = medication.id + 100000 // Простое смещение ID
        val snoozePendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Интент для открытия приложения по тапу на уведомление
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0, // RequestCode может быть 0, так как интент один для всех уведомлений
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_medication) // Маленькая иконка в статус-баре
            .setLargeIcon(largeIcon) // Фото лекарства
            .setContentTitle("Пора принимать \"${medication.name}\"")
            .setContentText(medication.description.ifEmpty { "Следуйте инструкции или предписанию врача" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(medication.description.ifEmpty { "Следуйте инструкции или предписанию врача" })) // Расширенный текст
            .setContentIntent(openAppPendingIntent) // Действие по тапу
            .addAction(
                R.drawable.ic_action_snooze,
                "Отложить на 10 минут",
                snoozePendingIntent
            )
            .setAutoCancel(true) // Закрывать уведомление после нажатия
            .build()

        // Показываем уведомление. ID уведомления совпадает с ID лекарства
        notificationManager.notify(medication.id, notification)
    }

    /**
     * Асинхронно загружает изображение по Uri и преобразует его в Bitmap для уведомления
     * Использует библиотеку Coil
     */
    private suspend fun getNotificationIcon(uri: Uri?): Bitmap? {
        if (uri == null) {
            // Если у лекарства нет фото, возвращаем иконку-заглушку
            return BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
        }
        return try {
            val imageLoader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false) // Важно для прямого доступа к Bitmap
                .transformations(CircleCropTransformation()) // Скругляем углы
                .build()

            val result = imageLoader.execute(request).drawable
            (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            // В случае ошибки загрузки возвращаем заглушку
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
        }
    }
}
