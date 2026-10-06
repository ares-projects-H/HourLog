package com.hourlog.app.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.hourlog.app.HourLogApplication
import com.hourlog.app.MainActivity
import com.hourlog.app.R
import com.hourlog.app.domain.*
import kotlinx.coroutines.*
import java.time.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val CHANNEL = "weekly_summary"
    const val WORK = "hourlog_weekly_reminder"
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun schedule(context: Context, prefs: Preferences, replace: Boolean = true) {
        val manager = WorkManager.getInstance(context)
        if (!prefs.reminderEnabled) { manager.cancelUniqueWork(WORK); return }
        val now = ZonedDateTime.now()
        val next = TimeCalculator.nextReminder(now, prefs)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("due" to next.toInstant().epochSecond,
                "day" to prefs.reminderDay, "hour" to prefs.reminderHour, "minute" to prefs.reminderMinute))
            .build()
        manager.enqueueUniqueWork(WORK, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HourLogApplication
        return try {
            val snapshot = app.repository.snapshot()
            val p = snapshot.preferences
            if (!p.reminderEnabled) return Result.success()
            // A replaced job must never send a stale reminder or overwrite a newer schedule.
            if (p.reminderDay != inputData.getInt("day", -1) || p.reminderHour != inputData.getInt("hour", -1) ||
                p.reminderMinute != inputData.getInt("minute", -1)) return Result.success()
            val now = ZonedDateTime.now()
            val due = Instant.ofEpochSecond(inputData.getLong("due", 0)).atZone(now.zone)
            if (now.toLocalDate() == due.toLocalDate() && !now.toInstant().isBefore(due.toInstant()) &&
                (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
                val monday = TimeCalculator.monday(now.toLocalDate())
                val minutes = snapshot.entries.filter { it.date >= monday && it.date <= now.toLocalDate() }.sumOf { it.paidMinutes }
                val intent = Intent(applicationContext, MainActivity::class.java).putExtra("open_week", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pending = PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val message = if(app.security.settings.value.mode != com.hourlog.app.security.LockMode.NONE)
                    applicationContext.getString(R.string.reminder_private)
                else applicationContext.getString(R.string.reminder_body, TimeCalculator.duration(minutes,p.hourFormat))
                val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification).setContentTitle(applicationContext.getString(R.string.reminder_title))
                    .setContentText(message).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(pending).setAutoCancel(true).build()
                applicationContext.getSystemService(NotificationManager::class.java).notify(1, notification)
            }
            // Append the next occurrence to the unique chain, preserving reboot persistence.
            val next = TimeCalculator.nextReminder(now, p)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("due" to next.toInstant().epochSecond, "day" to p.reminderDay,
                    "hour" to p.reminderHour, "minute" to p.reminderMinute)).build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(ReminderScheduler.WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.retry() }
    }
}

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { ReminderScheduler.schedule(context, (context.applicationContext as HourLogApplication).repository.snapshot().preferences) }
            finally { pending.finish() }
        }
    }
}
