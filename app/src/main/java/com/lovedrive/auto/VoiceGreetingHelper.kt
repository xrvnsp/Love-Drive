package com.lovedrive.auto

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.time.LocalTime
import java.util.Locale

class VoiceGreetingHelper(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingSpeak = false

    fun speakStartupGreeting() {
        if (!PhotoRepository.getVoiceGreetingEnabled(context)) return
        if (isReady) {
            speakNow()
        } else {
            pendingSpeak = true
            initTts()
        }
    }

    private fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                configureMaleVoice()
                if (pendingSpeak) {
                    pendingSpeak = false
                    speakNow()
                }
            }
        }
    }

    private fun configureMaleVoice() {
        val engine = tts ?: return
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setLanguage(Locale.US)

        // Prefer a male English voice if installed (Google TTS / Samsung TTS naming patterns)
        val maleHints = listOf(
            "male", "-m-", "iom", "tpd", "rjs", "gbd", "enc", "ene", "sfg", "fis"
        )
        val voices = try { engine.voices } catch (_: Exception) { null }
        val chosen = voices
            ?.filter { it.locale.language == "en" }
            ?.firstOrNull { v ->
                val n = v.name.lowercase(Locale.ROOT)
                maleHints.any { hint -> n.contains(hint) }
            }
        if (chosen != null) {
            engine.voice = chosen
        }

        // Warm, calm male cadence
        engine.setPitch(0.88f)
        engine.setSpeechRate(0.94f)
    }

    private fun speakNow() {
        val engine = tts ?: return
        val hour = LocalTime.now().hour
        val period = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val bday = PhotoRepository.getNextBirthdayReminder()
        val message = if (bday.isToday) {
            "$period, Kayalvizhi. Happy birthday ${bday.name}. Every drive begins with love."
        } else {
            "$period, Kayalvizhi. Every drive begins with love."
        }
        engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "love_drive_greeting")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
