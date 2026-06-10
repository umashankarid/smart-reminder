package com.komet.smartreminder.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.komet.smartreminder.data.db.AppDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val tomorrow = LocalDate.now().plusDays(1).toString()
        val appointments = db.appointmentDao().getByDateSync(tomorrow)

        if (appointments.isNotEmpty()) {
            val body = appointments.joinToString("\n") { a ->
                "${a.time.ifEmpty { "All day"}} - ${a.title}${if (a.location.isNotEmpty()) " @ ${a.location}" else ""}"
            }
            showNotification(
                "📅 Tomorrow: ${appointments.size} appointment${if (appointments.size > 1) "s" else ""}",
                body
            )
        }
        return Result.success()
    }

    private fun showNotification(title: String, body: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel("daily_reminder", "Daily Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, "daily_reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
    }
}

object ReminderScheduler {
    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var target = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
        if (target.isBefore(now)) target = target.plusDays(1)
        val delay = ChronoUnit.MINUTES.between(now, target)

        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .addTag("daily_reminder")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_reminder", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleDailyReminder(context, 20, 0)
        }
    }
}
