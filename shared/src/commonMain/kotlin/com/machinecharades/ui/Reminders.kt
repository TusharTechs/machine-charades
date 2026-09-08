package com.machinecharades.ui

import androidx.compose.runtime.Composable

/**
 * The daily nudge.
 *
 * A streak only works if something tells you it is at risk. Loss aversion is
 * what makes a streak worth keeping, and a streak nobody is reminded of is
 * just a number that quietly resets.
 *
 * Scheduled on the device rather than pushed from a server: there is nothing
 * to say that the phone does not already know — a new puzzle every day at the
 * same time — so a backend, a third-party SDK and a push certificate would all
 * be machinery in service of a message the clock could deliver. It also works
 * offline, costs nothing to run, and keeps the platform layer to one more thin
 * pair rather than a native dependency on both sides.
 *
 * Modelled as an effect rather than an object because that is what it is:
 * flipping [enabled] on schedules the reminder and asks for permission if the
 * OS wants it, flipping it off cancels. Nothing to hold, nothing to release.
 */
@Composable
expect fun DailyReminderEffect(enabled: Boolean, hour: Int = REMINDER_HOUR)

/**
 * 9am local.
 *
 * The game is a thirty-second morning ritual, so the nudge belongs with the
 * other morning ones rather than in the evening, when it competes with
 * everything else a phone wants at that hour.
 */
const val REMINDER_HOUR = 9

/** What the notification says. Kept here so both actuals cannot drift. */
internal const val REMINDER_TITLE = "Today's word is up"
internal const val REMINDER_BODY = "Thirty seconds. Keep the streak."
