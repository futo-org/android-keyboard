package org.futo.voiceinput.shared

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
import android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
import android.media.SoundPool
import java.io.Closeable

// soundPool.play returns 0 on failure
private const val SoundPoolPlayFailure = 0

// status in OnLoadCompleteListener is 0 when successful
private const val LoadStatusSuccess = 0

class SoundPlayer(
    private val context: Context
): Closeable {
    private val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(CONTENT_TYPE_SONIFICATION).build()
    ).build()

    private var startSound: Int = -1
    private var cancelSound: Int = -1

    init {
        startSound = soundPool.load(this.context, R.raw.start, 0)
        cancelSound = soundPool.load(this.context, R.raw.cancel, 0)
    }

    override fun close() {
        soundPool.release()
    }

    // Returns true if successful, zero if failed
    private fun playSound(id: Int): Boolean {
        return when(soundPool.play(id, 1.0f, 1.0f, 0, 0, 1.0f)) {
            SoundPoolPlayFailure -> false
            else -> true
        }
    }

    // Tries to play a sound. If it's not yet ready, plays it when it's ready
    private fun playSoundOrLoad(id: Int) {
        if (!playSound(id)) {
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if ((sampleId == id) && (status == LoadStatusSuccess)) {
                    playSound(id)
                }
            }
        }
    }

    fun playStartSound() {
        playSoundOrLoad(startSound)
    }

    fun playCancelSound() {
        playSoundOrLoad(cancelSound)
    }
}