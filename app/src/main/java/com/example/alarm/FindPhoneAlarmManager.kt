package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FindPhoneAlarmManager(private val context: Context) {

    private val tag = "FindPhoneAlarmManager"
    private var mediaPlayer: MediaPlayer? = null
    private var isVibrating = false

    private val _isAlarmActive = MutableStateFlow(false)
    val isAlarmActive: StateFlow<Boolean> = _isAlarmActive.asStateFlow()

    private val _triggerSource = MutableStateFlow<String?>(null)
    val triggerSource: StateFlow<String?> = _triggerSource.asStateFlow()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Trigger loud alarm and vibration when physical tracker button is pressed
     */
    fun startAlarm(sourceTrackerName: String) {
        if (_isAlarmActive.value) return // Already alarming

        _triggerSource.value = sourceTrackerName
        _isAlarmActive.value = true

        startAudioAlert()
        startVibration()
    }

    fun stopAlarm() {
        if (!_isAlarmActive.value) return

        _isAlarmActive.value = false
        _triggerSource.value = null

        stopAudioAlert()
        stopVibration()
    }

    private fun startAudioAlert() {
        try {
            // Find appropriate ringtone or alarm sound
            var alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start ringtone media player", e)
        }
    }

    private fun stopAudioAlert() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(tag, "Error stopping media player: ${e.message}")
        }
    }

    private fun startVibration() {
        try {
            isVibrating = true
            // Repeating pattern: wait 0ms, vibrate 400ms, sleep 200ms, vibrate 600ms, sleep 300ms
            val timings = longArrayOf(0, 400, 200, 600, 300)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, 0) // repeat at index 0
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(timings, 0)
            }
        } catch (e: Exception) {
            Log.w(tag, "Vibration failed: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            if (isVibrating) {
                vibrator?.cancel()
                isVibrating = false
            }
        } catch (e: Exception) {
            Log.w(tag, "Error cancelling vibration: ${e.message}")
        }
    }
}
