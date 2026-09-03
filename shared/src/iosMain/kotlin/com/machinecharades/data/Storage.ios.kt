package com.machinecharades.data

import platform.Foundation.NSUserDefaults

actual fun platformStorage(): Storage = object : Storage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun get(key: String): String? = defaults.stringForKey(key)
    override fun put(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}
