package com.machinecharades

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform