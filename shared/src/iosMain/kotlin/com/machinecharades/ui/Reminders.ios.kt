package com.machinecharades.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/** One id, so rescheduling replaces rather than stacks. */
private const val REQUEST_ID = "machine-charades-daily"

/**
 * UNUserNotificationCenter, with a calendar trigger that repeats daily.
 *
 * Permission is requested only when [enabled] turns true, never on launch. The
 * measured difference is large — asking at the moment someone has just felt the
 * game work converts far better than asking a stranger before they have played
 * — and iOS gives you exactly one chance to ask.
 *
 * Every failure here is silent. A game that broke because a notification could
 * not be scheduled would be a far worse bug than a missing reminder.
 */
@Composable
actual fun DailyReminderEffect(enabled: Boolean, hour: Int) {
    LaunchedEffect(enabled, hour) {
        val centre = UNUserNotificationCenter.currentNotificationCenter()
        if (!enabled) {
            centre.removePendingNotificationRequestsWithIdentifiers(listOf(REQUEST_ID))
            return@LaunchedEffect
        }
        centre.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { granted, _ ->
            if (!granted) return@requestAuthorizationWithOptions

            val content = UNMutableNotificationContent().apply {
                setTitle(REMINDER_TITLE)
                setBody(REMINDER_BODY)
            }
            val components = NSDateComponents().apply {
                setHour(hour.toLong())
                setMinute(0)
            }
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = REQUEST_ID,
                content = content,
                trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                    dateComponents = components,
                    repeats = true,
                ),
            )
            centre.removePendingNotificationRequestsWithIdentifiers(listOf(REQUEST_ID))
            centre.addNotificationRequest(request, null)
        }
    }
}
