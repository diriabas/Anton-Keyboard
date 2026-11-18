package com.diriabas.antonkeyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

class VoiceRecognitionActivity : Activity() {
    companion object {
        const val EXTRA_LANGUAGE = "anton.keyboard.extra.LANGUAGE"
        private const val REQUEST_RECOGNITION = 7104
        private const val REQUEST_MICROPHONE = 7105
    }

    private var launchAttempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val language = intent.getStringExtra(EXTRA_LANGUAGE) ?: "ru-RU"
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        } else launchRecognition(language)
    }

    private fun launchRecognition(language: String) {
        if (launchAttempted) return
        launchAttempted = true
        AntonInputMethodService.updateVoiceStatus("3")
        val recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите")
        }
        try {
            AntonInputMethodService.updateVoiceStatus("4")
            startActivityForResult(recognitionIntent, REQUEST_RECOGNITION)
        } catch (_: ActivityNotFoundException) {
            AntonInputMethodService.updateVoiceStatus("NA")
            Toast.makeText(this, "На устройстве не найдено приложение для системного голосового ввода", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            AntonInputMethodService.updateVoiceStatus("X")
            Toast.makeText(this, "Не удалось запустить голосовой ввод: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_RECOGNITION) {
            if (resultCode == RESULT_OK) {
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                AntonInputMethodService.deliverVoiceResult(results?.firstOrNull())
            } else AntonInputMethodService.deliverVoiceResult(null)
            finish()
        }
    }

    @Deprecated("Deprecated API retained for compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) launchRecognition(intent.getStringExtra(EXTRA_LANGUAGE) ?: "ru-RU") else {
                AntonInputMethodService.updateVoiceStatus("P!")
                Toast.makeText(this, "Нет разрешения на микрофон", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
