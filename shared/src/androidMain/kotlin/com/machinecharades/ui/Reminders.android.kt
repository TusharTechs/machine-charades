package com.machinecharades.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

private const val CHANNEL_ID = "daily"
private const val NOTIFICATION_ID = 1
private const val REQUEST_CODE = 1001

/**
 * AlarmManager plus a receiver, which is the plain way to say "every day at
 * nine" without dragging WorkManager in for one repeating alarm.
 *
 * Inexact on purpose. `setInexactRepeating` lets the OS batch this with other
 * wakeups: a few minutes of imprecision for battery, and a nudge to play a word
 * game has no business waking a device on the second.
 *
 * Deliberately no androidx dependency. NotificationCompat and ContextCompat
 * would each be one call, and the versions in this catalog now demand
 * compileSdk 37 — which AGP 9.1 cannot yet target. The platform APIs cover
 * everything at minSdk 24, so the reminder costs the binary nothing.
 *
 * Permission is requested only when [enabled] turns true, never on launch. The
 * measured opt-in difference between asking after someone's first win and
 * asking a stranger on first open is roughly two to one.
 */
@Composable
actual fun DailyReminderEffect(enabled: Boolean, hour: Int) {
    val context = LocalContext.current
    LaunchedEffect(enabled, hour) {
        if (!enabled) {
            cancel(context)
            return@LaunchedEffect
        }
        // Asked for, not waited on. The alarm is scheduled either way: if the
        // player declines, it simply fires into a system that drops it, and
        // turning reminders on again later asks once more.
        if (needsPermission(context)) {
            (context as? Activity)?.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE,
            )
        }
        schedule(context, hour)
    }
}

private fun needsPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun alarmIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun schedule(context: Context, hour: Int) {
    val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val next = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        // Today's slot may already have passed, in which case the first
        // reminder is tomorrow rather than a moment from now.
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    runCatching {
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            alarmIntent(context),
        )
    }
}

private fun cancel(context: Context) {
    val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    runCatching { alarms.cancel(alarmIntent(context)) }
}

/** Fires on the alarm and posts the notification. Registered in the manifest. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Daily puzzle", NotificationManager.IMPORTANCE_DEFAULT),
            )
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { open ->
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(REMINDER_TITLE)
            .setContentText(REMINDER_BODY)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
