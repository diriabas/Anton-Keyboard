package com.diriabas.antonkeyboard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast

class VoiceRecognitionActivity : Activity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var voiceLanguage = "ru-RU"
    private var resultSent = false
    private var recognitionStarted = false
    private var selectedServiceLabel = "системный сервис"

    private data class RecognitionProvider(
        val component: ComponentName,
        val label: String,
        val packageName: String
    )

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
        private const val ACTION_VOICE_RESULT =
            "com.diriabas.antonkeyboard.VOICE_RESULT"
        private const val EXTRA_VOICE_TEXT = "voice_text"
        private const val EXTRA_VOICE_LANGUAGE = "voice_language"
        private const val ERROR_TOO_MANY_REQUESTS_CODE = 10
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
            showAvailableProvidersAndStart()
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
            showAvailableProvidersAndStart()
        } else {
            showMessage(
                "Нет разрешения на микрофон. Разреши доступ в настройках приложения."
            )
            finish()
        }
    }

    private fun findRecognitionProviders(): List<RecognitionProvider> {
        return try {
            val queryIntent = Intent(RecognitionService.SERVICE_INTERFACE)
            val resolved: List<ResolveInfo> =
                packageManager.queryIntentServices(queryIntent, 0)

            resolved.mapNotNull { item ->
                val info = item.serviceInfo ?: return@mapNotNull null
                if (!info.enabled || !info.exported) return@mapNotNull null

                val component = ComponentName(info.packageName, info.name)
                val label = try {
                    info.loadLabel(packageManager)?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: info.name
                } catch (_: Exception) {
                    info.name
                }

                RecognitionProvider(
                    component = component,
                    label = label,
                    packageName = info.packageName
                )
            }.distinctBy { it.component.flattenToString() }
                .sortedWith(
                    compareBy<RecognitionProvider>(
                        { if (it.packageName.contains("google", true)) 0 else 1 },
                        { it.label.lowercase() }
                    )
                )
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun showAvailableProvidersAndStart() {
        if (isFinishing) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showMessage(
                "Android не сообщает о доступном сервисе распознавания речи."
            )
            finish()
            return
        }

        val providers = findRecognitionProviders()

        if (providers.isEmpty()) {
            selectedServiceLabel = "системный сервис (службы не обнаружены через PackageManager)"
            startRecognition(null)
            return
        }

        val entries = providers.map {
            "${it.label}\n${it.packageName}\n${it.component.className}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Найденные службы распознавания")
            .setMessage(
                "Выбери службу для проверки. Если не уверен, выбери Google " +
                    "или первый доступный вариант."
            )
            .setItems(entries) { _, which ->
                val provider = providers[which]
                selectedServiceLabel =
                    "${provider.label} (${provider.packageName})"
                startRecognition(provider.component)
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }

    private fun startRecognition(service: ComponentName?) {
        if (recognitionStarted || isFinishing) return
        recognitionStarted = true

        try {
            speechRecognizer = if (service != null) {
                SpeechRecognizer.createSpeechRecognizer(this, service)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }

            speechRecognizer?.setRecognitionListener(createRecognitionListener())

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

            if (speechRecognizer == null) {
                showMessage("Не удалось создать распознаватель речи.")
                finish()
                return
            }

            speechRecognizer?.startListening(recognitionIntent)

        } catch (e: Exception) {
            val details = e.localizedMessage
                ?.takeIf { it.isNotBlank() }
                ?: e.javaClass.simpleName
            showMessage("Не удалось запустить голосовой ввод: $details")
            finish()
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {

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
                if (!resultSent) showMessage(getErrorMessage(error))
                finish()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val recognizedText = matches?.firstOrNull()?.trim().orEmpty()

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
                "Сервис распознавания прервал запуск."
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
            ERROR_TOO_MANY_REQUESTS_CODE ->
                "Сервис распознавания отклонил запросы как слишком частые " +
                    "(код 10). Выбранная служба: $selectedServiceLabel. " +
                    "Подожди минуту и попробуй один раз."
            else ->
                "Ошибка распознавания речи (код $error). " +
                    "Выбранная служба: $selectedServiceLabel"
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
