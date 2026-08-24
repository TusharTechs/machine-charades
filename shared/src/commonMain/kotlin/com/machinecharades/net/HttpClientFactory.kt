package com.machinecharades.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Builds the platform's HTTP client.
 *
 * Exists because the engines need different configuration: Android has to be
 * told to prefer IPv4 (see the actual), while Darwin needs nothing.
 */
internal expect fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
