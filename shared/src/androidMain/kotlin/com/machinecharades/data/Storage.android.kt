package com.machinecharades.data

import android.content.Context

/**
 * SharedPreferences, which needs a Context the shared module has no way to ask
 * for. MainActivity hands one over before any composition runs; see
 * [AndroidStorage.install].
 */
object AndroidStorage {
    private var prefs: android.content.SharedPreferences? = null

    /** Call once, from the Activity, before the app renders. */
    fun install(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences("machine-charades", Context.MODE_PRIVATE)
        }
    }

    internal fun storage(): Storage = object : Storage {
        // Missing prefs means install() was never called. Returning null and
        // dropping writes degrades to "the game forgets", which is survivable;
        // throwing here would take down the round screen instead.
        override fun get(key: String): String? = prefs?.getString(key, null)
        override fun put(key: String, value: String) {
            prefs?.edit()?.putString(key, value)?.apply()
        }
    }
}

actual fun platformStorage(): Storage = AndroidStorage.storage()
