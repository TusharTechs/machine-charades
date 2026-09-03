package com.machinecharades

import androidx.compose.runtime.Composable

/**
 * Hands [Scoring.shareString] to the platform's own share sheet.
 *
 * Composable rather than a plain function because Android needs the Activity
 * context to launch the chooser, and the only honest way to get one is from the
 * composition. Returns a no-op-safe lambda: a failed share must never take the
 * result screen down with it.
 */
@Composable
expect fun rememberShareAction(): (String) -> Unit
