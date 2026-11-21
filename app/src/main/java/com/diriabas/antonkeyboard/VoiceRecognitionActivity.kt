package com.diriabas.antonkeyboard

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

class VoiceRecognitionActivity : Activity() {

    private var voiceLanguage = "ru-RU"
    private var requestLaunched = false

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
        private const val REQUEST_SPEECH_RECOGNITION = 1002

        private const val ACTION_VOICE_RESULT =
            "com.diriabas.antonkeyboard.VOICE_RESULT"
        private const val EXTRA_VOICE_TEXT = "voice_text"
        private const val EXTRA_VOICE_LANGUAGE = "voice_language"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceLanguage = intent.getStringExtra(EXTRA_VOICE_LANGUAGE)
            ?.takeIf { it == "ru-RU" || it == "en-US" }
            ?: "ru-RU"

        // The recognition Activity may handle microphone permission itself,
        // but retaining this check keeps the keyboard's existing permission flow.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            launchSystemRecognition()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_RECORD_AUDIO) return

        if (grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            launchSystemRecognition()
        } else {
            showMessage(
                "Нет разрешения на микрофон. Разреши доступ в настройках приложения."
            )
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun launchSystemRecognition() {
        if (requestLaunched || isFinishing) return
        requestLaunched = true

        val recognitionIntent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, voiceLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (voiceLanguage == "ru-RU") {
                    "Говори по-русски"
                } else {
                    "Speak in English"
                }
            )
        }

        try {
            startActivityForResult(
                recognitionIntent,
                REQUEST_SPEECH_RECOGNITION
            )
        } catch (_: ActivityNotFoundException) {
            showMessage(
                "На устройстве нет приложения, которое может открыть системное распознавание речи."
            )
            finish()
        } catch (e: Exception) {
            val details = e.localizedMessage
                ?.takeIf { it.isNotBlank() }
                ?: e.javaClass.simpleName
            showMessage("Не удалось открыть голосовой ввод: $details")
            finish()
        }
    }

    @Deprecated("Uses the Android activity-result callback for broad device compatibility.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_SPEECH_RECOGNITION) return

        if (resultCode == RESULT_OK) {
            val recognizedText = data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            if (recognizedText.isNotEmpty()) {
                sendResult(recognizedText)
            } else {
                showMessage("Речь не распознана. Попробуй ещё раз.")
            }
        } else {
            showMessage("Голосовой ввод отменён или распознавание не удалось.")
        }

        finish()
    }

    private fun sendResult(recognizedText: String) {
        val resultIntent = Intent(ACTION_VOICE_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_VOICE_TEXT, recognizedText)
        }
        sendBroadcast(resultIntent)
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
