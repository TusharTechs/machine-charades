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

    private companion object {
        const val KEY_SOUND = "sound"
        const val ON = "on"
        const val OFF = "off"
    }
}
