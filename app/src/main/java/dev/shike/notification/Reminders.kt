package dev.shike.notification

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.work.*
import dev.shike.*
import dev.shike.data.*
import dev.shike.domain.*
import java.time.*
import kotlinx.coroutines.*

object Reminders {
    fun intent(context: Context, id: String, title: String = "", time: Long = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java)
                .setData(Uri.parse("shike://reminder/$id"))
                .putExtra("id", id)
                .putExtra("title", title)
                .putExtra("time", time),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun cancel(context: Context, id: String) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(
            intent(context, id)
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(
            id.hashCode()
        )
    }

    fun schedule(context: Context, item: Item, settings: Appearance) {
        cancel(context, item.id)
        if (
            !settings.notifications ||
                !item.remind ||
                item.deletedAt != null ||
                item.kind !in listOf("TASK", "EVENT", "HABIT") ||
                item.state in listOf("DONE", "CANCELLED", "ARCHIVED")
        )
            return
        val now = System.currentTimeMillis()
        val at =
            if (item.kind == "HABIT") {
                if (!settings.habitReminders) return
                val rule =
                    runCatching { json.decodeFromString<RepeatRule>(item.rule) }.getOrNull()
                        ?: return
                val today = LocalDate.now(ZoneId.of(rule.zone))
                val first = if (Recurrence.instant(rule, today) > now) today else today.plusDays(1)
                Recurrence.nextOnOrAfter(rule, first)?.let { Recurrence.instant(rule, it) }
                    ?: return
            } else (item.due ?: return) - item.reminderMinutes * 60000L
        if (at <= now) return
        set(context, item.id, item.title, quietAdjusted(at, settings), item.kind == "TASK")
    }

    fun quietAdjusted(at: Long, settings: Appearance): Long {
        val z = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        val h = z.hour
        val s = settings.quietStart
        val e = settings.quietEnd
        val quiet = if (s == e) false else if (s > e) h >= s || h < e else h in s until e
        return if (quiet)
            z.toLocalDate()
                .plusDays(if (s > e && h >= s) 1 else 0)
                .atTime(e, 0)
                .atZone(z.zone)
                .toInstant()
                .toEpochMilli()
        else at
    }

    fun set(context: Context, id: String, title: String, at: Long, exact: Boolean) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (exact && manager.canScheduleExactAlarms())
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at,
                intent(context, id, title, at),
            )
        else
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at,
                intent(context, id, title, at),
            )
    }

    fun scheduleAll(context: Context, items: List<Item>, settings: Appearance) {
        val series = items.filter { it.kind == "SERIES" }.associateBy { it.id }
        items.forEach { item ->
            if (item.seriesId == null || TaskViews.seriesEnabled(series[item.seriesId]))
                schedule(context, item, settings)
            else cancel(context, item.id)
        }
        cancel(context, "review-daily")
        if (settings.notifications && settings.reviewReminders) {
            val now = ZonedDateTime.now()
            var next = now.withHour(20).withMinute(0).withSecond(0)
            if (next < now) next = next.plusDays(1)
            set(
                context,
                "review-daily",
                "留一点时间，回顾今天",
                quietAdjusted(next.toInstant().toEpochMilli(), settings),
                false,
            )
        }
    }

    fun show(context: Context, id: String, title: String) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("shike-reminders", "拾刻提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setData(Uri.parse("shike://item/$id"))
                    .putExtra("itemId", id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        nm.notify(
            id.hashCode(),
            Notification.Builder(context, "shike-reminders")
                .setSmallIcon(dev.shike.R.drawable.icon_foreground)
                .setContentTitle(title)
                .setContentText("轻点返回拾刻")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build(),
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext as ShikeApplication
        app.appScope.launch {
            try {
                val repo = app.repository
                val id = intent.getStringExtra("id") ?: return@launch
                val time = intent.getLongExtra("time", 0)
                val marker = "reminded:$id:$time"
                val items = repo.dao.allItems()
                val item = items.find { it.id == id }
                val s = repo.readSettings()
                if (!s.notifications) return@launch
                if (
                    id != "review-daily" &&
                        (item == null ||
                            item.deletedAt != null ||
                            item.state in listOf("DONE", "CANCELLED", "ARCHIVED") ||
                            item.seriesId != null && !TaskViews.seriesEnabled(items.find { it.id == item.seriesId }))
                )
                    return@launch
                if (repo.dao.allConfig().none { it.key == marker }) {
                    repo.dao.putConfig(Config(marker, "1"))
                    Reminders.show(context, id, item?.title ?: "留一点时间，回顾今天")
                }
                if (item?.kind == "HABIT") Reminders.schedule(context, item, s)
                if (item?.seriesId != null) repo.generateInstances()
                if (id == "review-daily") Reminders.scheduleAll(context, repo.dao.allItems(), s)
            } finally {
                result.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<MaintenanceWorker>().build())
    }
}

class MaintenanceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            val repo = (applicationContext as ShikeApplication).repository
            repo.generateInstances()
            val s = repo.readSettings()
            if (s.trashDays > 0) {
                val cutoff = System.currentTimeMillis() - s.trashDays * 86400000L
                repo.dao
                    .allItems()
                    .filter { it.deletedAt != null && it.deletedAt < cutoff }
                    .forEach { repo.erase(it) }
            }
            Reminders.scheduleAll(applicationContext, repo.dao.allItems(), s)
            repo.refreshPortableBackup()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
}
