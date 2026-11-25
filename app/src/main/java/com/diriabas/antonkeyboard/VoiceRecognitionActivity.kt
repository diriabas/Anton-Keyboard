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

    private var voiceLanguage = "ru-RU"
    private var speechRecognizer: SpeechRecognizer? = null
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

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            startSpeechRecognition()
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
            startSpeechRecognition()
        } else {
            finishWithMessage("Для голосового ввода нужен доступ к микрофону.")
        }
    }

    private fun startSpeechRecognition() {
        if (isFinishing) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            finishWithMessage(
                "На устройстве не найден сервис распознавания речи. " +
                    "Проверь настройки Google голосового ввода."
            )
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(createRecognitionListener())
            }

            val recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, voiceLanguage)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }

            speechRecognizer?.startListening(recognitionIntent)
        } catch (e: SecurityException) {
            finishWithMessage("Нет доступа к микрофону. Проверь разрешение для Klavio.")
        } catch (e: Exception) {
            finishWithMessage(
                "Не удалось запустить распознавание речи: " +
                    (e.localizedMessage ?: "неизвестная ошибка")
            )
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                val message = if (voiceLanguage == "ru-RU") {
                    "Говори по-русски"
                } else {
                    "Speak in English"
                }
                Toast.makeText(this@VoiceRecognitionActivity, message, Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO ->
                        "Ошибка записи звука. Проверь микрофон."
                    SpeechRecognizer.ERROR_CLIENT ->
                        "Сервис распознавания остановил запрос."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Нет разрешения на использование микрофона."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Не удалось подключиться к сервису распознавания. Проверь интернет."
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "Речь не распознана. Попробуй ещё раз."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        "Сервис распознавания занят. Попробуй через несколько секунд."
                    SpeechRecognizer.ERROR_SERVER ->
                        "Сервис распознавания временно недоступен."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Речь не обнаружена. Попробуй ещё раз."
                    else ->
                        "Ошибка распознавания речи (код $error)."
                }
                finishWithMessage(message)
            }

            override fun onResults(results: Bundle?) {
                val recognizedText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()

                if (recognizedText.isNullOrEmpty()) {
                    finishWithMessage("Не удалось распознать речь. Попробуй ещё раз.")
                } else {
                    sendResult(recognizedText)
                    finishAndRelease()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
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

    private fun finishWithMessage(message: String) {
        if (isFinishing) return
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finishAndRelease()
    }

    private fun finishAndRelease() {
        releaseSpeechRecognizer()
        if (!isFinishing) finish()
    }

    private fun releaseSpeechRecognizer() {
        val recognizer = speechRecognizer ?: return
        speechRecognizer = null

        try {
            recognizer.cancel()
        } catch (_: Exception) {
            // The recognizer may already have stopped.
        }

        try {
            recognizer.destroy()
        } catch (_: Exception) {
            // Ignore cleanup errors.
        }
    }

    override fun onDestroy() {
        releaseSpeechRecognizer()
        super.onDestroy()
    }
}
