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
    private var voiceLanguage = "ru-RU"
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
            ?.takeIf { it == "ru-RU" || it == "en-US" }
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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_RECORD_AUDIO) return

        if (grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceRecognition()
        } else {
            showMessage("Нет разрешения на микрофон. Разреши доступ в настройках приложения.")
            finish()
        }
    }

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showMessage(
                "На устройстве не найден сервис распознавания речи. " +
                    "Проверь, установлен ли голосовой сервис."
            )
            finish()
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        showMessage(
                            if (voiceLanguage == "ru-RU") {
                                "Говори по-русски..."
                            } else {
                                "Speak in English..."
                            }
                        )
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
                            showMessage("Речь не распознана. Попробуй ещё раз.")
                        }

                        finish()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
            )

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

            speechRecognizer?.startListening(recognitionIntent)
                ?: run {
                    showMessage("Не удалось создать распознаватель речи.")
                    finish()
                }

        } catch (e: Exception) {
            val details = e.localizedMessage
                ?.takeIf { it.isNotBlank() }
                ?: e.javaClass.simpleName

            showMessage("Не удалось запустить голосовой ввод: $details")
            finish()
        }
    }

    private fun sendResult(recognizedText: String) {
        if (resultSent) return
        resultSent = true

        val resultIntent = Intent(ACTION_VOICE_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_VOICE_TEXT, recognizedText)
        }

        sendBroadcast(resultIntent)
    }

    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO ->
                "Ошибка микрофона или записи звука."

            SpeechRecognizer.ERROR_CLIENT ->
                "Сервис распознавания прервал запуск. Попробуй ещё раз."

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Нет разрешения на использование микрофона."

            SpeechRecognizer.ERROR_NETWORK ->
                "Не удалось подключиться к сервису распознавания. Проверь интернет."

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Сервис распознавания не ответил вовремя. Проверь интернет."

            SpeechRecognizer.ERROR_NO_MATCH ->
                "Не удалось разобрать речь. Скажи фразу ещё раз."

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Распознавание уже занято. Подожди немного и повтори."

            SpeechRecognizer.ERROR_SERVER ->
                "Сервис распознавания вернул ошибку."

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "Не услышал речь. Начни говорить сразу после сигнала."

            else ->
                "Ошибка распознавания речи (код $error)."
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null
        super.onDestroy()
    }
}
