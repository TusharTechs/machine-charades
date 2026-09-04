package com.machinecharades.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * AudioTrack rather than MediaPlayer or SoundPool, because both of those want a
 * Context or a file on disk and this needs neither: the cue arrives as WAV bytes
 * and the PCM inside can go straight to the device.
 *
 * USAGE_GAME on sonification, so it plays at the volume the player has already
 * chosen for games and ducks correctly against anything else.
 */
actual class SoundPlayer {

    actual fun play(wav: ByteArray) {
        // 44-byte canonical header, which is what tools writes.
        if (wav.size <= WAV_HEADER) return
        val pcm = wav.copyOfRange(WAV_HEADER, wav.size)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        // Released when the cue ends rather than held: a couple of hundred
        // milliseconds each, at most three a round.
        track.setNotificationMarkerPosition(pcm.size / BYTES_PER_FRAME)
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) { runCatching { t?.release() } }
                override fun onPeriodicNotification(t: AudioTrack?) = Unit
            },
        )
        track.play()
    }

    actual fun release() = Unit

    private companion object {
        const val WAV_HEADER = 44
        const val SAMPLE_RATE = 22050
        const val BYTES_PER_FRAME = 2
    }
}

actual fun createSoundPlayer(): SoundPlayer = SoundPlayer()
