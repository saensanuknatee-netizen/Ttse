package com.example

import com.example.ai.NavigationSafetyValidator
import com.example.model.SpeakingStyle
import com.example.model.ThaiParticleMode
import com.example.model.VoiceGender
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSafetyValidatorTest {

    @Test
    fun urgentCommandsTriggerFastPath() {
        assertTrue(NavigationSafetyValidator.isUrgentManeuver("เลี้ยวซ้าย"))
        assertTrue(NavigationSafetyValidator.isUrgentManeuver("ชิดขวา"))
        assertTrue(NavigationSafetyValidator.isUrgentManeuver("อีก 100 เมตร เลี้ยวขวา"))
        assertTrue(NavigationSafetyValidator.isUrgentManeuver("Turn left now"))

        // Advance distance & cruising prompts should go through AI Naturalization Path
        assertFalse(NavigationSafetyValidator.isUrgentManeuver("ในอีก 800 เมตร เลี้ยวขวาเข้าสู่ ถนนสุขุมวิท"))
        assertFalse(NavigationSafetyValidator.isUrgentManeuver("ขับตรงไปตามเส้นทางอีก 6 กิโลเมตร"))
        assertFalse(NavigationSafetyValidator.isUrgentManeuver("In 500 meters, turn left onto Rama IV Road"))
    }

    @Test
    fun safetyValidatorRejectsAlteredNumbersOrDirections() {
        val raw = "ในอีก 800 เมตร เลี้ยวขวาเข้าสู่ ถนนสุขุมวิท"
        assertTrue(
            NavigationSafetyValidator.verifySafetyPreserved(
                raw,
                "อีกประมาณ 800 เมตร เตรียมตัวเลี้ยวขวาเข้าถนนสุขุมวิทนะคะ"
            )
        )
        // Changed distance -> must reject
        assertFalse(
            NavigationSafetyValidator.verifySafetyPreserved(
                raw,
                "อีกประมาณ 500 เมตร เตรียมตัวเลี้ยวขวาเข้าถนนสุขุมวิทนะคะ"
            )
        )
        // Inverted direction -> must reject
        assertFalse(
            NavigationSafetyValidator.verifySafetyPreserved(
                raw,
                "อีกประมาณ 800 เมตร เตรียมตัวเลี้ยวซ้ายเข้าถนนสุขุมวิทนะคะ"
            )
        )
    }

    @Test
    fun thaiParticlePolicyRespectsUrgencyAndGender() {
        val (urgentOut, urgentApplied) = NavigationSafetyValidator.applyThaiParticlePolicy(
            text = "เลี้ยวซ้ายนะคะ",
            gender = VoiceGender.FEMALE,
            particleMode = ThaiParticleMode.SMART_CONTEXT,
            isUrgent = true,
            isPreviewMode = true
        )
        assertFalse(urgentApplied)
        assertFalse(urgentOut.endsWith("นะคะ"))

        val (maleOut, maleApplied) = NavigationSafetyValidator.applyThaiParticlePolicy(
            text = "ช่วงนี้ขับตรงยาวๆ ไปตามเส้นทางอีก 6 กิโลเมตร",
            gender = VoiceGender.MALE,
            particleMode = ThaiParticleMode.SMART_CONTEXT,
            isUrgent = false,
            isPreviewMode = true
        )
        assertTrue(maleApplied)
        assertTrue(maleOut.endsWith("นะครับ"))
    }

    @Test
    fun bilingualSsmlWrapsEnglishLandmarksAndAddsBreak() {
        val ssml = NavigationSafetyValidator.buildBilingualSsml(
            spokenText = "อีกประมาณ 400 เมตร เตรียมตัวเลี้ยวซ้ายเข้า Terminal 21 Asok นะคะ",
            detectedLanguage = "th-TH-mixed",
            style = SpeakingStyle.WARM,
            expressiveness = 0.7f
        )
        assertTrue(ssml.startsWith("<speak>") && ssml.endsWith("</speak>"))
        assertTrue(ssml.contains("""<lang xml:lang="en-US">Terminal 21 Asok</lang>"""))
        assertTrue(ssml.contains("<break time="))
    }
}
