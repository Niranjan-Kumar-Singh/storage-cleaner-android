package com.example.storagecleaner.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.storagecleaner.MainActivity
import java.text.DateFormat
import java.util.Date

class ScanReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReminderManager.showNotification(context)
        // Re-schedule next interval if recurring
        ReminderManager.rescheduleNext(context)
    }
}

object ReminderManager {
    private const val PREF_NAME = "storage_cleaner_prefs"
    private const val KEY_INTERVAL_DAYS = "reminder_interval_days"
    private const val KEY_NEXT_RUN = "reminder_next_run"
    const val CHANNEL_ID = "storage_cleaner_reminders"
    private const val NOTIFICATION_ID = 1001

    enum class Interval(val days: Int, val label: String) {
        OFF(0, "Off"),
        WEEKLY(7, "Every 7 days"),
        BIWEEKLY(14, "Every 14 days"),
        MONTHLY(30, "Every 30 days")
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getCurrentInterval(context: Context): Interval {
        val days = getPrefs(context).getInt(KEY_INTERVAL_DAYS, 0)
        return Interval.values().firstOrNull { it.days == days } ?: Interval.OFF
    }

    fun getNextReminderText(context: Context): String {
        val interval = getCurrentInterval(context)
        if (interval == Interval.OFF) return "Reminders disabled"
        val nextRun = getPrefs(context).getLong(KEY_NEXT_RUN, 0L)
        return if (nextRun > System.currentTimeMillis()) {
            "Next: " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(nextRun))
        } else {
            "Due now"
        }
    }

    fun setInterval(context: Context, interval: Interval) {
        val prefs = getPrefs(context)
        prefs.edit().putInt(KEY_INTERVAL_DAYS, interval.days).apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ScanReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (interval == Interval.OFF) {
            alarmManager?.cancel(pendingIntent)
            prefs.edit().remove(KEY_NEXT_RUN).apply()
        } else {
            val triggerAt = System.currentTimeMillis() + (interval.days * 24L * 60L * 60L * 1000L)
            prefs.edit().putLong(KEY_NEXT_RUN, triggerAt).apply()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager?.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (_: Exception) {
                // Best effort scheduling
            }
        }
    }

    fun rescheduleNext(context: Context) {
        val interval = getCurrentInterval(context)
        if (interval != Interval.OFF) {
            setInterval(context, interval)
        }
    }

    fun showNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Storage Cleaner Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to scan storage and remove junk files"
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("Storage Cleaner 🧹")
            .setContentText("It's time to scan your storage and remove duplicate or junk files.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Keep your phone running smoothly by cleaning up old screenshots, chat media, and duplicate files.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }
}
