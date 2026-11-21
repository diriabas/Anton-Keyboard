
package com.diriabas.antonkeyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast

class VoiceRecognitionActivity : Activity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var permissionRequested = false

    private var voiceLanguage = "ru-RU"

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceLanguage = intent.getStringExtra(
            AntonInputMethodService.EXTRA_VOICE_LANGUAGE
        ) ?: "ru-RU"

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionRequested = true
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            startVoiceRecognition()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startVoiceRecognition()
            } else {
                showMessage("Нет разрешения на использование микрофона")
                finish()
            }
        }
    }

    private fun startVoiceRecognition() {
        if (isFinishing) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showMessage(
                "На устройстве нет доступного сервиса распознавания речи"
            )
            finish()
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        showMessage("Говорите...")
                    }

                    override fun onBeginningOfSpeech() {
                        isListening = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        showMessage(errorMessage(error))
                        finish()
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false

                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val recognizedText = matches
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                        if (recognizedText.isNotEmpty()) {
                            sendResult(recognizedText)
                        } else {
                            showMessage("Речь не распознана")
                        }

                        finish()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )

            val recognitionIntent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    voiceLanguage
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    voiceLanguage
                )
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )
                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    if (voiceLanguage == "ru-RU") {
                        "Говорите по-русски"
                    } else {
                        "Speak in English"
                    }
                )
            }

            speechRecognizer?.startListening(recognitionIntent)

        } catch (e: Exception) {
            showMessage("Не удалось запустить распознавание речи")
            finish()
        }
    }

    private fun sendResult(text: String) {
        val resultIntent = Intent(
            AntonInputMethodService.ACTION_VOICE_RESULT
        ).apply {
            setPackage(packageName)
            putExtra(
                AntonInputMethodService.EXTRA_VOICE_TEXT,
                text
            )
        }

        sendBroadcast(resultIntent)
    }

    private fun errorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO ->
                "Ошибка записи звука"

            SpeechRecognizer.ERROR_CLIENT ->
                "Ошибка запуска распознавания"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Нет разрешения на микрофон"

            SpeechRecognizer.ERROR_NETWORK ->
                "Ошибка подключения к сети"

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Превышено время ожидания сети"

            SpeechRecognizer.ERROR_NO_MATCH ->
                "Речь не распознана"

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Распознавание уже запущено"

            SpeechRecognizer.ERROR_SERVER ->
                "Ошибка сервера распознавания"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "Вы ничего не сказали"

            else ->
                "Ошибка распознавания речи"
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        isListening = false

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null
        super.onDestroy()
    }
}
