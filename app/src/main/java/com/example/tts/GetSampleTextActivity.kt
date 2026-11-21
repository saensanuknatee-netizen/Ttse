package com.example.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Handles android.speech.tts.engine.GET_SAMPLE_TEXT when users tap "Listen to an example"
 * in Android Settings > Text-to-speech output.
 */
class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang = intent?.getStringExtra("language")?.lowercase(Locale.ROOT).orEmpty()
        val sampleText = when (lang) {
            "tha", "th" -> "ในอีก 800 เมตร เลี้ยวขวาเข้าสู่ ถนนสุขุมวิท"
            "eng", "en" -> "In 500 meters, turn left onto Rama IV Road."
            else -> "ในอีก 800 เมตร เลี้ยวขวาเข้าสู่ ถนนสุขุมวิท"
        }
        val resultIntent = Intent().apply {
            putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sampleText)
        }
        setResult(TextToSpeech.LANG_AVAILABLE, resultIntent)
        finish()
    }
}
