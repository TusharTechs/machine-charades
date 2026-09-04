package com.machinecharades.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import machinecharades.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Which sound a round-ending event makes. */
enum class Cue { HIT, MISS }

/**
 * Plays a short WAV.
 *
 * Audio is one of the few things that genuinely differs between the platforms —
 * there is no Compose Multiplatform audio API — so this is an expect/actual
 * pair rather than something contorted into common code. Both actuals are a
 * dozen lines.
 */
expect class SoundPlayer {
    fun play(wav: ByteArray)
    fun release()
}

expect fun createSoundPlayer(): SoundPlayer

/**
 * Loads the two cues once and returns something that plays them.
 *
 * Failure here is silent by design: a game that refuses to reveal the answer
 * because an audio device was busy would be a far worse bug than a missing
 * sound. Everything below returns a no-op rather than throwing.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberSoundCues(enabled: Boolean): (Cue) -> Unit {
    val player = remember { runCatching { createSoundPlayer() }.getOrNull() }
    var hit by remember { mutableStateOf<ByteArray?>(null) }
    var miss by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(Unit) {
        hit = runCatching { Res.readBytes("files/hit.wav") }.getOrNull()
        miss = runCatching { Res.readBytes("files/miss.wav") }.getOrNull()
    }

    return { cue ->
        if (enabled) {
            val bytes = if (cue == Cue.HIT) hit else miss
            if (bytes != null && player != null) runCatching { player.play(bytes) }
        }
    }
}
