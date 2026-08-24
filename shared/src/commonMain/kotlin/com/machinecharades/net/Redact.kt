package com.machinecharades.net

/**
 * Strips credentials out of text before it is shown or logged.
 *
 * Firebase's REST API takes the Web API key as a query parameter, so the key
 * appears in every request URL — and platform HTTP errors quote that URL back.
 * On iOS the raw NSURLError text was rendered straight onto the screen, key
 * included. The key is not a high-value secret (it ships in the binary and only
 * identifies the project) but putting it on a screen or in a log is careless,
 * and screenshots travel.
 */
internal fun redactSecrets(text: String): String =
    text.replace(Regex("""(?i)(key=)[^&\s,)"']+"""), "$1***")
