package com.machinecharades.data

/**
 * Player settings, kept apart from PlayerStats.
 *
 * Same store, different thing: stats are a record of what happened and are
 * rewritten after every round, settings are chosen once and read on launch.
 * Folding a preference into the stats blob would mean a corrupt stats file
 * silently resets your choices too.
 */
class Prefs(private val storage: Storage = platformStorage()) {

    /** Defaults on. A cue you did not ask for is easier to turn off than one you never discover. */
    var soundOn: Boolean
        get() = storage.get(KEY_SOUND) != OFF
        set(value) = storage.put(KEY_SOUND, if (value) ON else OFF)

    /**
     * Whether the first-run explainer has been shown.
     *
     * Defaults to false so an existing player who updates sees it once too —
     * they were the ones who could not tell what the game wanted.
     */
    var hasSeenIntro: Boolean
        get() = storage.get(KEY_INTRO) == ON
        set(value) = storage.put(KEY_INTRO, if (value) ON else OFF)

    /**
     * Whether the daily reminder is scheduled.
     *
     * Defaults off, unlike sound. A notification you did not ask for is a very
     * different imposition from a sound you did not ask for, and the OS
     * permission prompt is a one-shot: spending it before a player has any
     * reason to want the reminder wastes it.
     */
    var remindersOn: Boolean
        get() = storage.get(KEY_REMIND) == ON
        set(value) = storage.put(KEY_REMIND, if (value) ON else OFF)

    /** Whether the reminder has been offered, so it is offered exactly once. */
    var hasAskedReminders: Boolean
        get() = storage.get(KEY_REMIND_ASKED) == ON
        set(value) = storage.put(KEY_REMIND_ASKED, if (value) ON else OFF)

    private companion object {
        const val KEY_SOUND = "sound"
        const val KEY_INTRO = "intro"
        const val KEY_REMIND = "remind"
        const val KEY_REMIND_ASKED = "remind_asked"
        const val ON = "on"
        const val OFF = "off"
    }
}
