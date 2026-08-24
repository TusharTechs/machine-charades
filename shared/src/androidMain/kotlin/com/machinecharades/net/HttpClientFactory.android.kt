package com.machinecharades.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Orders IPv4 ahead of IPv6 rather than filtering IPv6 out.
 *
 * The Android emulator resolves AAAA records but has no IPv6 route at all —
 * `ping6` to a Google resolver is 100% loss while IPv4 answers in 9ms — so a
 * client that tries the AAAA address first fails to connect on a network that
 * is otherwise fine. That produced "Failed to connect to
 * identitytoolkit.googleapis.com/[2001:4860:4840:400::]:443" on first run.
 *
 * Sorting rather than filtering matters: an IPv6-only network is rare but real,
 * and dropping AAAA entirely would make the app unusable there. This way IPv6
 * stays available as a fallback and only loses its priority.
 */
private val ipv4First = object : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        Dns.SYSTEM.lookup(hostname).sortedBy { it !is Inet4Address }
}

internal actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        block()
        engine { config { dns(ipv4First) } }
    }
