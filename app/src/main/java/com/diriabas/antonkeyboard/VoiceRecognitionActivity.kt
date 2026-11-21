
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
    private var voiceLanguage: String = "ru-RU"
    private var resultSent = false

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001

        private const val ACTION_VOICE_RESULT =
            "com.diriabas.antonkeyboard.VOICE_RESULT"

        private const val EXTRA_VOICE_TEXT = "voice_text"
        private const val EXTRA_VOICE_LANGUAGE = "voice_language"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceLanguage = intent.getStringExtra(EXTRA_VOICE_LANGUAGE)
            ?: "ru-RU"

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
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
            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startVoiceRecognition()
            } else {
                showMessage("Нет разрешения на микрофон")
                finish()
            }
        }
    }

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showMessage(
                "На устройстве нет сервиса распознавания речи"
            )
            finish()
            return
        }

        try {
            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        showMessage("Говорите...")
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        if (!resultSent) {
                            showMessage(getErrorMessage(error))
                        }
                        finish()
                    }

                    override fun onResults(results: Bundle?) {
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

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) {}

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )

            val recognitionIntent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

            recognitionIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            recognitionIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                voiceLanguage
            )

            recognitionIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                voiceLanguage
            )

            recognitionIntent.putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )

            recognitionIntent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
            )

            recognitionIntent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (voiceLanguage == "ru-RU") {
                    "Говорите по-русски"
                } else {
                    "Speak in English"
                }
            )

            speechRecognizer?.startListening(recognitionIntent)

        } catch (e: Exception) {
            showMessage(
                "Не удалось запустить распознавание речи"
            )
            finish()
        }
    }

    private fun sendResult(recognizedText: String) {
        if (resultSent) return

        resultSent = true

        val resultIntent = Intent()

        resultIntent.action = ACTION_VOICE_RESULT
        resultIntent.setPackage(packageName)

        resultIntent.putExtra(
            EXTRA_VOICE_TEXT,
            recognizedText
        )

        sendBroadcast(resultIntent)
    }

    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO ->
                "Ошибка записи звука"

            SpeechRecognizer.ERROR_CLIENT ->
                "Ошибка запуска распознавания"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Нет разрешения на микрофон"

            SpeechRecognizer.ERROR_NETWORK ->
                "Ошибка сети"

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Превышено время ожидания сети"

            SpeechRecognizer.ERROR_NO_MATCH ->
                "Речь не распознана"

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Распознавание уже запущено"

            SpeechRecognizer.ERROR_SERVER ->
                "Ошибка сервера распознавания"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "Не удалось услышать речь"

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
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        super.onDestroy()
    }
}
