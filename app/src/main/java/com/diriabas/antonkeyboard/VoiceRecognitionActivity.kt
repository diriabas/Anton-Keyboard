
package com.diriabas.antonkeyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class VoiceRecognitionActivity : Activity() {

    companion object {
        const val EXTRA_LANGUAGE = "voice_language"
        const val ACTION_VOICE_RESULT =
            "com.diriabas.antonkeyboard.VOICE_RESULT"
        const val EXTRA_TEXT = "recognized_text"

        private const val REQUEST_MICROPHONE = 2001
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var statusText: TextView
    private var language = "ru-RU"
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        language = intent.getStringExtra(EXTRA_LANGUAGE) ?: "ru-RU"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
            setBackgroundColor(0xFF171D2B.toInt())
        }

        statusText = TextView(this).apply {
            text = "🎙️ Подготовка микрофона..."
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(12, 20, 12, 24)
        }

        val cancelButton = Button(this).apply {
            text = "Отмена"
            isAllCaps = false
            setOnClickListener {
                finish()
            }
        }

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(cancelButton)

        setContentView(root)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_MICROPHONE
            )
        } else {
            startRecognition()
        }
    }

    private fun startRecognition() {
        if (started) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showError("На устройстве не найден сервис распознавания речи.")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        statusText.text = "🎙️ Слушаю... Говорите"
                    }

                    override fun onBeginningOfSpeech() {
                        statusText.text = "🎙️ Распознаю речь..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        statusText.text = "Обрабатываю речь..."
                    }

                    override fun onError(error: Int) {
                        showError(
                            when (error) {
                                SpeechRecognizer.ERROR_AUDIO ->
                                    "Ошибка записи звука."
                                SpeechRecognizer.ERROR_NETWORK ->
                                    "Ошибка сети."
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                    "Время ожидания сети истекло."
                                SpeechRecognizer.ERROR_NO_MATCH ->
                                    "Не удалось распознать речь. Попробуй ещё раз."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                    "Речь не обнаружена."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                    "Нет разрешения на микрофон."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                    "Сервис распознавания занят."
                                else ->
                                    "Ошибка распознавания: $error"
                            }
                        )
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val recognizedText = matches
                            ?.firstOrNull()
                            ?.trim()

                        if (!recognizedText.isNullOrEmpty()) {
                            sendResult(recognizedText)
                        } else {
                            showError("Речь не распознана.")
                        }
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    language
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            started = true
            statusText.text = "🎙️ Запускаю распознавание..."
            speechRecognizer?.startListening(recognitionIntent)

        } catch (e: Exception) {
            showError("Не удалось запустить распознавание речи.")
        }
    }

    private fun sendResult(text: String) {
        val resultIntent = Intent(ACTION_VOICE_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_TEXT, text)
        }

        sendBroadcast(resultIntent)
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
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

        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startRecognition()
            } else {
                showError("Для голосового ввода нужен доступ к микрофону.")
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}
