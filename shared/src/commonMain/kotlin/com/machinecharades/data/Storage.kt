package com.machinecharades.data

/**
 * The smallest key-value store the game needs. One string in, one string out.
 *
 * Deliberately not a database. Everything persisted here is a single serialised
 * PlayerStats, rewritten whole after each round — perhaps 2KB after a year of
 * daily play. A schema would cost more than it saves.
 */
interface Storage {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** The platform's own store: SharedPreferences on Android, NSUserDefaults on iOS. */
expect fun platformStorage(): Storage
