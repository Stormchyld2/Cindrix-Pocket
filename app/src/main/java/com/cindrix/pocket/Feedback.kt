package com.cindrix.pocket

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Beep and buzz when a barcode has been read, so you know it registered without looking. */
object Feedback {

    fun scanOk(context: Context, sound: Boolean, vibrate: Boolean) {
        if (vibrate) {
            try {
                buzz(context)
            } catch (e: Exception) {
                // No vibrator, or the phone refused. Not worth stopping a scan for.
            }
        }
        if (sound) {
            try {
                beep()
            } catch (e: Exception) {
                // No audio available right now.
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz(context: Context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // Uses the notification volume, so the beep follows the phone's silent mode.
    private fun beep() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
    }
}
