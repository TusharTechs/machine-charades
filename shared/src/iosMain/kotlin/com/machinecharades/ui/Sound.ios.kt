package com.machinecharades.ui

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * AVAudioPlayer reads the WAV header itself, so unlike Android there is no PCM
 * unpacking here.
 *
 * The session is Ambient on purpose: this is a game cue, not content. Ambient
 * respects the silent switch and leaves whatever the player was already
 * listening to alone, which for a thirty-second daily game is the only polite
 * option.
 */
actual class SoundPlayer {

    private var current: AVAudioPlayer? = null

    init {
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
            AVAudioSession.sharedInstance().setActive(true, null)
        }
    }

    actual fun play(wav: ByteArray) {
        if (wav.isEmpty()) return
        val data = wav.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = wav.size.toULong())
        }
        // Held in a field so it is not collected mid-playback, which silently
        // truncates the cue.
        current = AVAudioPlayer(data = data, error = null).also { it.play() }
    }

    actual fun release() {
        current?.stop()
        current = null
    }
}

actual fun createSoundPlayer(): SoundPlayer = SoundPlayer()
