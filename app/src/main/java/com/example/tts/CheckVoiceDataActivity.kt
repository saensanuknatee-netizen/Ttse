package com.example.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/**
 * Handles android.speech.tts.engine.CHECK_TTS_DATA so Android System Settings
 * and Google Maps recognize th-TH, en-US, and en-GB voices as ready and installed.
 */
class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val availableVoices = arrayListOf(
            "tha-THA",
            "eng-USA",
            "eng-GBR"
        )
        val resultIntent = Intent().apply {
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices)
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf())
        }
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, resultIntent)
        finish()
    }
}
