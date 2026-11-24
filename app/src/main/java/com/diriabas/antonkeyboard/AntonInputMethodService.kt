
package com.diriabas.antonkeyboard

import android.inputmethodservice.InputMethodService
import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.ClipboardManager
import android.content.Context
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.InputType
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.textservice.TextServicesManager
import android.view.textservice.SpellCheckerSession
import android.view.textservice.TextInfo
import android.view.textservice.SuggestionsInfo
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import org.json.JSONArray

class AntonInputMethodService : InputMethodService() {

    private data class Flake(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speed: Float,
        var drift: Float
    )

    private var isRussian = true
    private var isSymbols = false
    private var isEmoji = false
    private var isAI = false
    private var isSettings = false

    private var shift = false
    private var capsLock = false
    private var lastShiftTap = 0L

    private var keyboardRoot: LinearLayout? = null

    private val voiceResultAction = "com.diriabas.antonkeyboard.VOICE_RESULT"
    private val voiceTextExtra = "voice_text"
    private val voiceLanguageExtra = "voice_language"
    private var voiceReceiverRegistered = false

    private val voiceResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != voiceResultAction) return
            val recognizedText = intent.getStringExtra(voiceTextExtra)?.trim().orEmpty()
            if (recognizedText.isEmpty()) return

            val textToInsert = if (recognizedText.lastOrNull()?.isWhitespace() == true) {
                recognizedText
            } else {
                "$recognizedText "
            }

            val connection = currentInputConnection
            if (connection != null) {
                connection.commitText(textToInsert, 1)
                updateSuggestions()
            } else {
                Toast.makeText(
                    this@AntonInputMethodService,
                    "Поле ввода больше не активно",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    private var suggestionsRow: LinearLayout? = null
    private var spellCheckerSession: SpellCheckerSession? = null
    private var spellCheckerLocale: Locale? = null
    private var requestedSpellWord: String = ""
    private var systemSuggestions: List<String> = emptyList()

    private val clipboardHistory = mutableListOf<String>()
    private val maxClipboardItems = 20

    private val personalWords = mutableListOf<String>()
    private var autoCorrectionEnabled = true
    private var autoPunctuationSpaceEnabled = true
    private var autoCapitalizationEnabled = true

    private var selectedTheme = "dark_gray"
    private var glassTheme = false
    private var backgroundColor = Color.rgb(14, 17, 29)
    private var keyColor = Color.rgb(34, 40, 59)
    private var accentColor = Color.rgb(67, 205, 255)
    private var violetColor = Color.rgb(153, 111, 255)

    private val corrections = mapOf(
        "превет" to "привет",
        "приветт" to "привет",
        "спосибо" to "спасибо",
        "спасибоо" to "спасибо",
        "пожалуста" to "пожалуйста",
        "пажалуйста" to "пожалуйста",
        "севодня" to "сегодня",
        "сигодня" to "сегодня",
        "завтро" to "завтра",
        "кароче" to "короче",
        "щас" to "сейчас",
        "чево" to "чего",
        "чтобыы" to "чтобы",
        "здравствуйтее" to "здравствуйте",
        "извените" to "извините",
        "извеняюсь" to "извиняюсь",
        "вообщем" to "в общем",
        "потаму" to "потому",
        "пачему" to "почему",
        "сабака" to "собака",
        "кошак" to "кошка",
        "teh" to "the",
        "adn" to "and",
        "recieve" to "receive",
        "becouse" to "because",
        "definately" to "definitely",
        "seperate" to "separate",
        "thier" to "their",
        "wierd" to "weird",
        "freind" to "friend",
        "langauge" to "language",
        "wich" to "which"
    )

    private val russianWords = listOf(
        "привет", "приветик", "приветствую", "привычка", "привычный",
        "привык", "привыкнуть", "привести", "приведу", "приведёт",
        "приводить", "приводит", "привезти", "привязать", "привлечение",
        "пожалуйста", "пожалуй", "пожаловаться",
        "спасибо", "спасибо большое",
        "здравствуйте", "добро", "добрый", "доброе",
        "день", "дня", "вечер", "вечера",
        "утро", "утром", "ночь",
        "хорошо", "хороший", "хорошая",
        "отлично", "отличный",
        "сегодня", "завтра", "вчера",
        "работа", "работать", "работаю",
        "дом", "дома", "домой",
        "человек", "люди", "друг", "друзья",
        "люблю", "любимый", "любимая",
        "можно", "можешь", "могу",
        "нужно", "нужен", "нужна",
        "почему", "потому", "потом",
        "конечно", "точно", "сейчас",
        "клавиатура", "телефон", "компьютер",
        "интересно", "понятно", "правильно",
        "извини", "извините", "поздравляю"
    )

    private val englishWords = listOf(
        "hello", "help", "he", "her", "here",
        "thanks", "thank", "thankful",
        "please", "pleasant",
        "good", "great", "goodbye",
        "morning", "afternoon", "evening",
        "night", "today", "tomorrow",
        "yesterday", "time", "think",
        "thing", "there", "their",
        "this", "that", "these",
        "what", "when", "where",
        "why", "which", "who",
        "work", "working", "world",
        "wonderful", "welcome",
        "friend", "friends", "family",
        "love", "lovely", "like",
        "make", "making", "maybe",
        "message", "mobile", "music",
        "computer", "keyboard",
        "beautiful", "because",
        "before", "between", "better",
        "interesting", "important",
        "understand", "unfortunately"
    )

    private val extraRussianWords = listOf(
        "абсолютно", "автоматически", "аккуратно", "активно", "актуально",
        "анализ", "аппарат", "аргумент", "безопасность", "близко",
        "быстро", "важно", "вариант", "вместе", "возможность", "вопрос",
        "время", "всегда", "встреча", "выглядит", "говорить", "готово",
        "давно", "давай", "действительно", "делать", "деньги", "дорога",
        "достаточно", "другой", "если", "естественно", "желаю", "жизнь",
        "зависит", "закон", "замечательно", "запомнить", "здорово",
        "значит", "изменить", "изображение", "информация", "исправить",
        "кажется", "каждый", "качество", "когда", "количество", "команда",
        "конкретно", "который", "красиво", "кроме", "лучше", "любовь",
        "магазин", "маленький", "машина", "медленно", "место", "мнение",
        "может", "моменты", "много", "наверное", "надеюсь", "название",
        "написать", "настоящий", "начать", "недавно", "необходимо",
        "неправильно", "новый", "обязательно", "обычно", "объяснить",
        "общение", "оказалось", "около", "опять", "ответ", "отправить",
        "первый", "перевод", "пожалуйста", "позже", "получается",
        "помощь", "посмотреть", "последний", "почему-то", "правильно",
        "прекрасно", "приложение", "пример", "проблема", "продолжить",
        "просто", "работает", "разговор", "размер", "разница", "решение",
        "сделать", "система", "сколько", "следующий", "слово", "смотреть",
        "совершенно", "сообщение", "спокойно", "спросить", "сразу",
        "стараться", "становится", "сторона", "телефон", "теперь",
        "точный", "удобно", "уже", "улучшить", "устройство", "функция",
        "хорошо", "хотеть", "часто", "человек", "через", "чтобы", "эта", "это"
    )

    private val extraEnglishWords = listOf(
        "about", "above", "account", "across", "actually", "address",
        "after", "again", "against", "almost", "already", "always",
        "another", "answer", "anything", "around", "available", "beautiful",
        "believe", "between", "build", "business", "change", "check",
        "choose", "clear", "coming", "common", "complete", "correct",
        "could", "different", "doing", "during", "early", "easy",
        "enough", "especially", "everything", "example", "experience",
        "family", "favorite", "first", "follow", "found", "friendship",
        "getting", "going", "happen", "having", "heard", "however",
        "including", "information", "inside", "instead", "keyboard",
        "language", "later", "learning", "little", "looking", "making",
        "maybe", "message", "mistake", "moment", "money", "morning",
        "nothing", "number", "often", "online", "outside", "people",
        "perhaps", "phone", "place", "please", "possible", "probably",
        "problem", "quickly", "really", "remember", "right", "screen",
        "second", "something", "sometimes", "started", "still", "system",
        "taking", "their", "there", "these", "thing", "think", "through",
        "together", "trying", "under", "until", "using", "usually",
        "version", "wanted", "watch", "which", "without", "wonderful", "would", "written"
    )

    override fun onCreate() {
        super.onCreate()
        loadClipboardHistory()
        loadKeyboardSettings()
        loadPersonalWords()
        loadTheme()

        val voiceFilter = IntentFilter(voiceResultAction)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                voiceResultReceiver,
                voiceFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(voiceResultReceiver, voiceFilter)
        }
        voiceReceiverRegistered = true
    }

    // Do not switch to Android's fullscreen extract editor in landscape.
    // That mode can replace or hide the app's own text-entry field while the IME is open.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        spellCheckerSession?.close()
        spellCheckerSession = null
        if (voiceReceiverRegistered) {
            try {
                unregisterReceiver(voiceResultReceiver)
            } catch (_: Exception) {
            }
            voiceReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun launchVoiceRecognition() {
        try {
            val voiceIntent = Intent(
                this,
                VoiceRecognitionActivity::class.java
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(
                    voiceLanguageExtra,
                    if (isRussian) "ru-RU" else "en-US"
                )
            }
            startActivity(voiceIntent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Не удалось открыть голосовой ввод",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateInputView(): View {
        // Match Android's navigation bar to the keyboard background.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window?.window?.apply {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                navigationBarColor = backgroundColor
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    navigationBarDividerColor = backgroundColor
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    isNavigationBarContrastEnforced = false
                }
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), 0, dp(5), dp(6))
            setBackgroundColor(backgroundColor)
        }

        // Keep the keyboard above Android's navigation bar / gesture area.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val navigationBarBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            val safeBottom = maxOf(dp(6), navigationBarBottom)
            view.setPadding(dp(5), 0, dp(5), safeBottom)
            insets
        }
        root.requestApplyInsets()

        keyboardRoot = root
        renderKeyboard(root)

        // Keep the IME window close to the actual keyboard content height.
        window?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return root
    }

    private fun loadKeyboardSettings() {
        val prefs = getSharedPreferences("anton_keyboard", Context.MODE_PRIVATE)
        autoCorrectionEnabled = prefs.getBoolean("auto_correction", true)
        autoPunctuationSpaceEnabled = prefs.getBoolean("auto_punctuation_space", true)
        autoCapitalizationEnabled = prefs.getBoolean("auto_capitalization", true)
    }

    private fun loadTheme() {
        val prefs = getSharedPreferences("anton_keyboard", Context.MODE_PRIVATE)

        // Klavio Neon is the default only when no theme has ever been selected.
        // An existing user choice (for example, gray or dark_gray) is preserved.
        val savedTheme = prefs.getString("keyboard_theme", null)
        // Move the previous gray default to Klavio Neon once; other saved
        // theme choices remain untouched.
        selectedTheme = if (savedTheme == null || savedTheme == "gray") {
            "klavio_neon"
        } else {
            savedTheme
        }
        if (selectedTheme !in setOf(
                "klavio_neon", "gray", "dark_gray", "blue",
                "light_blue", "light_green", "glass", "new_year"
            )
        ) {
            selectedTheme = "klavio_neon"
        }

        glassTheme = selectedTheme == "glass"
        applyThemeColors()
    }

    private fun saveTheme(theme: String) {
        selectedTheme = theme
        glassTheme = theme == "glass"
        getSharedPreferences("anton_keyboard", Context.MODE_PRIVATE).edit()
            .putString("keyboard_theme", theme)
            .apply()
        applyThemeColors()
        keyboardRoot?.setBackgroundColor(backgroundColor)
        window?.window?.navigationBarColor = backgroundColor
        refresh()
    }

    private fun applyThemeColors() {
        when (selectedTheme) {
            "klavio_neon" -> {
                // Deep navy base with electric cyan and vivid blue accents.
                backgroundColor = Color.rgb(9, 13, 29)
                keyColor = Color.rgb(20, 43, 82)
                accentColor = Color.rgb(55, 220, 255)
                violetColor = Color.rgb(48, 119, 255)
            }
            "gray" -> {
                backgroundColor = Color.rgb(92, 96, 104)
                keyColor = Color.rgb(119, 124, 133)
                accentColor = Color.rgb(225, 229, 235)
                violetColor = Color.rgb(157, 165, 178)
            }
            "dark_gray" -> {
                backgroundColor = Color.rgb(35, 38, 42)
                keyColor = Color.rgb(68, 72, 77)
                accentColor = Color.rgb(205, 210, 216)
                violetColor = Color.rgb(91, 145, 238)
            }
            "blue" -> {
                backgroundColor = Color.rgb(8, 31, 66)
                keyColor = Color.rgb(20, 62, 112)
                accentColor = Color.rgb(77, 177, 255)
                violetColor = Color.rgb(67, 125, 230)
            }
            "light_blue" -> {
                backgroundColor = Color.rgb(190, 220, 242)
                keyColor = Color.rgb(218, 237, 250)
                accentColor = Color.rgb(48, 119, 174)
                violetColor = Color.rgb(91, 153, 211)
            }
            "light_green" -> {
                backgroundColor = Color.rgb(196, 222, 199)
                keyColor = Color.rgb(220, 239, 221)
                accentColor = Color.rgb(53, 117, 75)
                violetColor = Color.rgb(105, 164, 119)
            }
            "glass" -> {
                backgroundColor = Color.rgb(18, 24, 39)
                keyColor = Color.argb(155, 115, 150, 190)
                accentColor = Color.argb(210, 205, 231, 255)
                violetColor = Color.argb(185, 130, 155, 255)
            }
            "new_year" -> {
                backgroundColor = Color.rgb(7, 19, 43)
                keyColor = Color.rgb(22, 48, 79)
                accentColor = Color.rgb(125, 218, 255)
                violetColor = Color.rgb(183, 91, 190)
            }
        }
    }

    private fun saveKeyboardSettings() {
        getSharedPreferences("anton_keyboard", Context.MODE_PRIVATE).edit()
            .putBoolean("auto_correction", autoCorrectionEnabled)
            .putBoolean("auto_punctuation_space", autoPunctuationSpaceEnabled)
            .putBoolean("auto_capitalization", autoCapitalizationEnabled)
            .apply()
    }

    private fun loadPersonalWords() {
        val prefs = getSharedPreferences("anton_keyboard", Context.MODE_PRIVATE)
        val saved = prefs.getString("personal_words", "[]") ?: "[]"
        try {
            val array = JSONArray(saved)
            personalWords.clear()
            for (i in 0 until array.length()) {
                val word = array.optString(i).trim()
                if (word.isNotEmpty() && word !in personalWords) personalWords.add(word)
            }
        } catch (_: Exception) {
            personalWords.clear()
        }
    }

    private fun savePersonalWords() {
        val array = JSONArray()
        personalWords.forEach { array.put(it) }
        getSharedPreferences("anton_keyboard", Context.MODE_PRIVATE).edit()
            .putString("personal_words", array.toString())
            .apply()
    }

    // Long-pressing a suggestion asks before removing it from the personal dictionary.
    private fun confirmRemovePersonalWord(word: String) {
        val savedWord = personalWords.firstOrNull {
            it.equals(word.trim(), ignoreCase = true)
        } ?: return

        AlertDialog.Builder(this)
            .setTitle("Удалить слово?")
            .setMessage("Удалить «$savedWord» из личного словаря?")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                val removed = personalWords.removeAll {
                    it.equals(savedWord, ignoreCase = true)
                }
                if (removed) {
                    savePersonalWords()
                    Toast.makeText(
                        this,
                        "Слово «$savedWord» удалено",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateSuggestions()
                }
            }
            .show()
    }

    private fun addPersonalWord(word: String) {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return
        if (!Regex("^[А-Яа-яЁёA-Za-z-]+$").matches(cleaned)) {
            Toast.makeText(this, "Используй одно слово без пробелов и символов", Toast.LENGTH_SHORT).show()
            return
        }
        if (personalWords.any { it.equals(cleaned, ignoreCase = true) }) {
            Toast.makeText(this, "Это слово уже есть в словаре", Toast.LENGTH_SHORT).show()
            return
        }
        personalWords.add(0, cleaned)
        savePersonalWords()
        Toast.makeText(this, "Слово добавлено", Toast.LENGTH_SHORT).show()
        showSettings()
    }

    /**
     * Learns a completed word that is not present in the active built-in dictionary.
     * Learning happens only when the user finishes the word (space/punctuation/enter),
     * never while the user is still typing it.
     */
    private fun rememberUnknownWord(word: String) {
        val cleaned = word.trim()
        if (cleaned.length < 2) return
        if (!Regex("^[А-Яа-яЁёA-Za-z-]+$").matches(cleaned)) return
        if (personalWords.any { it.equals(cleaned, ignoreCase = true) }) return

        val normalized = cleaned.lowercase(Locale.ROOT)
        val dictionary = if (isRussian) {
            russianWords + extraRussianWords
        } else {
            englishWords + extraEnglishWords
        }
        if (dictionary.any { it.equals(normalized, ignoreCase = true) }) return

        personalWords.add(0, cleaned)
        if (personalWords.size > 1000) {
            personalWords.subList(1000, personalWords.size).clear()
        }
        savePersonalWords()
    }

    private fun loadClipboardHistory() {
        val prefs = getSharedPreferences(
            "anton_keyboard",
            Context.MODE_PRIVATE
        )

        val saved = prefs.getString("clipboard_history", "[]") ?: "[]"

        try {
            val array = JSONArray(saved)
            clipboardHistory.clear()

            for (i in 0 until array.length()) {
                val item = array.optString(i)
                if (item.isNotBlank()) {
                    clipboardHistory.add(item)
                }
            }
        } catch (_: Exception) {
            clipboardHistory.clear()
        }
    }

    private fun saveClipboardHistory() {
        val array = JSONArray()

        clipboardHistory.forEach { item ->
            array.put(item)
        }

        getSharedPreferences(
            "anton_keyboard",
            Context.MODE_PRIVATE
        ).edit()
            .putString("clipboard_history", array.toString())
            .apply()
    }

    private fun addClipboardItem(text: String) {
        val cleaned = text.trim()

        if (cleaned.isEmpty()) return

        clipboardHistory.remove(cleaned)
        clipboardHistory.add(0, cleaned)

        while (clipboardHistory.size > maxClipboardItems) {
            clipboardHistory.removeAt(clipboardHistory.lastIndex)
        }

        saveClipboardHistory()
    }

    private fun captureCurrentClipboard() {
        val manager = getSystemService(
            CLIPBOARD_SERVICE
        ) as ClipboardManager

        val clip = if (manager.hasPrimaryClip()) {
            manager.primaryClip
        } else {
            null
        }

        val text = clip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()

        if (!text.isNullOrBlank()) {
            addClipboardItem(text)
        }
    }

    private fun renderKeyboard(root: LinearLayout) {
        root.removeAllViews()
        suggestionsRow = null

        // Festive decoration appears only in the New Year theme.
        if (selectedTheme == "new_year") {
            addChristmasDecoration(root)
        }

        // Keep word suggestions above the shortcut toolbar (emoji, clipboard,
        // AI, language and settings). Do not show them in other modes.
        if (!isSettings && !isAI && !isEmoji && !isSymbols) {
            showSuggestions(root)
        }

        // Compact shortcut strip: no branding or language badge.
        val toolbar = newRow()

        addKey(toolbar, "😊", 1f, keyColor, keyHeight = 28) {
            isEmoji = !isEmoji
            isAI = false
            isSettings = false
            isSymbols = false
            refresh()
        }

        addKey(toolbar, "📋", 1f, keyColor, keyHeight = 28) {
            isEmoji = false
            isAI = false
            isSettings = false
            showClipboard()
        }

        addKey(toolbar, "✦", 1f, violetColor, keyHeight = 28) {
            isAI = !isAI
            isEmoji = false
            isSettings = false
            isSymbols = false
            refresh()
        }

        addKey(toolbar, "🎙", 0.9f, violetColor, keyHeight = 28) {
            launchVoiceRecognition()
        }

        addKey(toolbar, "🌐", 1f, keyColor, keyHeight = 28) {
            isRussian = !isRussian
            isSymbols = false
            isEmoji = false
            isAI = false
            isSettings = false
            refresh()
        }

        addKey(toolbar, "⚙", 0.72f, keyColor, keyHeight = 28) {
            isSettings = !isSettings
            isEmoji = false
            isAI = false
            isSymbols = false
            if (isSettings) showSettings() else refresh()
        }

        root.addView(toolbar)

        when {
            isSettings -> showSettings()
            isAI -> showAI(root)
            isEmoji -> showEmoji(root)
            else -> showLetters(root)
        }
    }

    private fun addChristmasDecoration(root: LinearLayout) {
        // A real animated decoration view: a curved string of colorful lights
        // and independently falling snowflakes. Animation stops when detached.
        root.addView(ChristmasDecorationView(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        ))
    }

    private inner class ChristmasDecorationView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val flakes = ArrayList<Flake>()
        private val bulbColors = intArrayOf(
            Color.rgb(255, 74, 89), Color.rgb(255, 205, 67),
            Color.rgb(91, 226, 142), Color.rgb(75, 181, 255),
            Color.rgb(205, 115, 255)
        )
        private var phase = 0f
        private var running = false
        private val frame = object : Runnable {
            override fun run() {
                if (!isAttachedToWindow) return
                phase += 0.12f
                invalidate()
                postDelayed(this, 45L)
            }
        }

        init {
            flakes.addAll((0 until 32).map { i ->
                Flake(
                    x = (i * 47 % 997) / 997f,
                    y = (i * 31 % 991) / 991f,
                    radius = 1.8f + (i % 4) * 0.7f,
                    speed = 0.004f + (i % 5) * 0.0014f,
                    drift = if (i % 2 == 0) 0.0015f else -0.0012f
                )
            })
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (!running) {
                running = true
                removeCallbacks(frame)
                post(frame)
            }
        }

        override fun onDetachedFromWindow() {
            running = false
            removeCallbacks(frame)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            // Fine, curved wire with warm glow.
            val wire = Path().apply {
                moveTo(0f, h * 0.20f)
                cubicTo(w * 0.25f, h * 0.62f, w * 0.72f, h * 0.62f, w, h * 0.20f)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.5f.toInt().coerceAtLeast(1)).toFloat()
            paint.color = Color.rgb(160, 190, 225)
            canvas.drawPath(wire, paint)

            // Color bulbs pulse independently along the wire.
            paint.style = Paint.Style.FILL
            for (i in 0 until 13) {
                val t = i / 12f
                val x = w * t
                val y = h * (0.20f + 0.42f * (1f - 4f * (t - 0.5f) * (t - 0.5f)))
                val pulse = 0.72f + 0.28f * kotlin.math.sin(phase + i * 0.82f).toFloat().coerceAtLeast(0f)
                val color = bulbColors[i % bulbColors.size]
                paint.color = Color.argb((65 * pulse).toInt(), Color.red(color), Color.green(color), Color.blue(color))
                canvas.drawCircle(x, y, dp(10).toFloat(), paint)
                paint.color = color
                canvas.drawCircle(x, y, dp(4).toFloat() * pulse, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(x - dp(1).toFloat(), y - dp(1).toFloat(), dp(0.8f.toInt()).toFloat().coerceAtLeast(1f), paint)
            }

            // Snowflakes drift down at different speeds; each is drawn as a small star.
            paint.color = Color.rgb(220, 241, 255)
            paint.strokeWidth = dp(1).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            paint.style = Paint.Style.STROKE
            flakes.forEach { flake ->
                val x = flake.x * w
                val y = flake.y * h
                val r = dp(flake.radius.toInt().coerceAtLeast(1)).toFloat()
                canvas.drawLine(x - r, y, x + r, y, paint)
                canvas.drawLine(x, y - r, x, y + r, paint)
                canvas.drawLine(x - r * .7f, y - r * .7f, x + r * .7f, y + r * .7f, paint)
                canvas.drawLine(x - r * .7f, y + r * .7f, x + r * .7f, y - r * .7f, paint)
                flake.y += flake.speed
                flake.x += flake.drift + kotlin.math.sin(phase + y * .02f).toFloat() * 0.00025f
                if (flake.y > 1.08f) {
                    flake.y = -0.08f
                    flake.x = ((flake.x * 997f + 173f) % 997f) / 997f
                }
                if (flake.x < -0.05f) flake.x = 1.05f
                if (flake.x > 1.05f) flake.x = -0.05f
            }
        }
    }

    private fun showLetters(root: LinearLayout) {
        val rows = when {
            isSymbols -> listOf(
                listOf("1","2","3","4","5","6","7","8","9","0"),
                listOf("@","#","$","%","&","-","+","(",")","/"),
                listOf("*","\"","'",";",":","!","?")
            )

            isRussian -> listOf(
                listOf("й","ц","у","к","е","н","г","ш","щ","з","х"),
                listOf("ф","ы","в","а","п","р","о","л","д","ж","э"),
                listOf("я","ч","с","м","и","т","ь","б","ю","ё")
            )

            else -> listOf(
                listOf("q","w","e","r","t","y","u","i","o","p"),
                listOf("a","s","d","f","g","h","j","k","l"),
                listOf("z","x","c","v","b","n","m")
            )
        }

        rows.forEachIndexed { index, keys ->
            val row = newRow()

            if (index == 2) {
                addKey(
                    row,
                    if (capsLock) "⇪" else "⇧",
                    1.3f,
                    if (shift || capsLock || shouldCapitalizeNext()) violetColor else keyColor
                ) {
                    handleShift()
                }
            }

            keys.forEach { key ->
                addKey(
                    row,
                    displayLetter(key),
                    1f,
                    longPressAction = {
                        val secondary = secondarySymbol(key.lowercase(Locale.ROOT))
                        if (secondary.isNotEmpty()) {
                            currentInputConnection?.commitText(secondary, 1)
                            isSymbols = false
                            refresh()
                        }
                    }
                ) {
                    val wasSymbols = isSymbols
                    if (wasSymbols && key in listOf(",", ".", "!", "?", ";", ":")) {
                        commitPunctuation(key)
                    } else {
                        currentInputConnection?.commitText(displayLetter(key), 1)
                    }
                    // Keep the symbols/numbers layout active after every key.
                    // Otherwise the first digit switches back to letters and
                    // multi-digit values (for example 100) cannot be entered.

                    if (shift && !capsLock && !wasSymbols) {
                        shift = false
                    }
                    if (wasSymbols) refresh() else updateSuggestions()
                }
            }

            if (index == 2) {
                addKey(
                    row,
                    "⌫",
                    1.3f,
                    violetColor,
                    repeatAction = { deleteBeforeCursor(); updateSuggestions() }
                ) {
                    deleteBeforeCursor()
                    updateSuggestions()
                }
            }

            root.addView(row)
        }

        val bottom = newRow()

        addKey(bottom, if (isSymbols) "ABC" else "?123", 1.3f) {
            isSymbols = !isSymbols
            refresh()
        }

        addKey(bottom, ",", 0.8f) {
            commitPunctuation(",")
            isSymbols = false
            refresh()
        }

        addKey(bottom, if (isRussian) "Русский" else "English", 4f, keyColor, keyHeight = 44) {
            commitSpaceWithCorrection()
            refresh()
        }

        addKey(bottom, ".", 0.8f) {
            commitPunctuation(".")
            isSymbols = false
            refresh()
        }

        addKey(bottom, "↵", 1.2f, violetColor) {
            rememberUnknownWord(getCurrentWord())
            currentInputConnection?.commitText("\n", 1)
            refresh()
        }

        root.addView(bottom)
    }

    private fun showSuggestions(root: LinearLayout) {
        val word = getCurrentWord()
        if (word != requestedSpellWord) {
            requestedSpellWord = word
            systemSuggestions = emptyList()
            requestSystemSpellCheck(word)
        }
        val suggestions = (getSuggestions(word) + systemSuggestions)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .filter { !it.equals(word, ignoreCase = true) }
            .take(5)

        // Do not reserve an empty 20dp row: it creates a visible gap above the keys.
        if (suggestions.isEmpty()) return

        val row = newRow()
        suggestionsRow = row
        suggestions.forEach { suggestion ->
            addKey(
                row, suggestion, 1f, keyColor,
                longPressAction = {
                    // Ask for confirmation before removing a learned word.
                    confirmRemovePersonalWord(suggestion)
                }
            ) {
                // A normal tap accepts the suggestion and keeps it in the personal dictionary.
                replaceCurrentWord(suggestion)
                updateSuggestions()
            }
        }
        root.addView(row, 0)
    }

    // Update only the suggestion strip; rebuilding the full keyboard on every
    // keystroke can cause visible input lag on some devices.
    private fun updateSuggestions() {
        val root = keyboardRoot ?: return
        if (isSymbols || isEmoji || isAI || isSettings) return

        val word = getCurrentWord()
        if (word != requestedSpellWord) {
            requestedSpellWord = word
            systemSuggestions = emptyList()
            requestSystemSpellCheck(word)
        }
        renderSuggestionRow(root)
    }

    private fun renderSuggestionRow(root: LinearLayout) {
        suggestionsRow?.let { oldRow ->
            root.removeView(oldRow)
            suggestionsRow = null
        }

        if (isEmailAddressContext()) return

        val word = getCurrentWord()
        val suggestions = (getSuggestions(word) + systemSuggestions)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .filter { !it.equals(word, ignoreCase = true) }
            .take(5)

        if (suggestions.isEmpty()) return

        val row = newRow()
        suggestionsRow = row
        suggestions.forEach { suggestion ->
            addKey(
                row, suggestion, 1f, keyColor,
                longPressAction = {
                    // Ask for confirmation before removing a learned word.
                    confirmRemovePersonalWord(suggestion)
                }
            ) {
                // A normal tap accepts the suggestion and keeps it in the personal dictionary.
                replaceCurrentWord(suggestion)
                systemSuggestions = emptyList()
                requestedSpellWord = ""
                updateSuggestions()
            }
        }
        root.addView(row, 0)
    }

    private fun requestSystemSpellCheck(word: String) {
        if (word.length < 3) return
        try {
            val locale = if (isRussian) Locale("ru") else Locale.US
            if (spellCheckerSession == null || spellCheckerLocale != locale) {
                spellCheckerSession?.close()
                val manager = getSystemService(
                    Context.TEXT_SERVICES_MANAGER_SERVICE
                ) as? TextServicesManager ?: return
                spellCheckerSession = manager.newSpellCheckerSession(
                    null,
                    locale,
                    object : SpellCheckerSession.SpellCheckerSessionListener {
                        override fun onGetSuggestions(results: Array<SuggestionsInfo>) {
                            val currentWord = requestedSpellWord
                            if (currentWord.isBlank()) return
                            val found = mutableListOf<String>()
                            results.forEach { info ->
                                for (i in 0 until info.suggestionsCount) {
                                    val candidate = info.getSuggestionAt(i)?.trim().orEmpty()
                                    if (candidate.isNotEmpty() && !candidate.equals(currentWord, true)) {
                                        found.add(candidate)
                                    }
                                }
                            }
                            systemSuggestions = found.distinctBy { it.lowercase(Locale.ROOT) }.take(5)
                            keyboardRoot?.let { root ->
                                if (!isSymbols && !isEmoji && !isAI && !isSettings && getCurrentWord() == currentWord) {
                                    renderSuggestionRow(root)
                                }
                            }
                        }

                        override fun onGetSentenceSuggestions(results: Array<android.view.textservice.SentenceSuggestionsInfo>) {
                            // Word-level suggestions are used by this keyboard.
                        }
                    },
                    true
                )
                spellCheckerLocale = locale
            }
            spellCheckerSession?.getSuggestions(TextInfo(word), 5)
        } catch (_: Exception) {
            // Some Huawei builds do not expose a system spell checker.
            // Built-in dictionaries and fuzzy matching remain available.
        }
    }

    private fun getCurrentWord(): String {
        val before = currentInputConnection
            ?.getTextBeforeCursor(60, 0)
            ?.toString() ?: ""

        return Regex("""[А-Яа-яЁёA-Za-z]+$""")
            .find(before)
            ?.value ?: ""
    }

    private fun getSuggestions(word: String): List<String> {
        if (word.isBlank()) return emptyList()

        val normalized = word.lowercase(Locale.ROOT)
        val dictionary = if (isRussian) {
            russianWords + extraRussianWords
        } else {
            englishWords + extraEnglishWords
        }
        val results = mutableListOf<String>()

        personalWords
            .filter { it.lowercase(Locale.ROOT).startsWith(normalized) && !it.equals(normalized, ignoreCase = true) }
            .forEach { if (it !in results) results.add(it) }

        corrections[normalized]?.let { if (it !in results) results.add(it) }

        dictionary
            .filter { it.startsWith(normalized) && !it.equals(normalized, ignoreCase = true) }
            .sortedBy { it.length }
            .forEach { if (it !in results) results.add(it) }

        // Fuzzy matching helps when one or two letters were mistyped.
        if (results.size < 5 && normalized.length >= 4) {
            val maxDistance = if (normalized.length >= 7) 2 else 1
            dictionary.asSequence()
                .distinct()
                .filter { kotlin.math.abs(it.length - normalized.length) <= maxDistance }
                .map { it to editDistance(normalized, it) }
                .filter { it.second in 1..maxDistance }
                .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.length })
                .forEach { pair ->
                    if (results.size < 5 && pair.first !in results) results.add(pair.first)
                }
        }

        return results.take(5).map { suggestion ->
            if (word.firstOrNull()?.isUpperCase() == true) {
                suggestion.replaceFirstChar { it.uppercase() }
            } else {
                suggestion
            }
        }
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun replaceCurrentWord(suggestion: String) {
        val connection = currentInputConnection ?: return
        val word = getCurrentWord()

        if (word.isNotEmpty()) {
            connection.deleteSurroundingText(word.length, 0)
        }

        // Accepting a suggestion completes the word and inserts one trailing space,
        // so the user can immediately continue typing the next word.
        connection.commitText("$suggestion ", 1)
    }

    private fun deleteBeforeCursor() {
        val connection = currentInputConnection ?: return
        val selected = connection.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            connection.commitText("", 1)
        } else {
            connection.deleteSurroundingText(1, 0)
        }
    }

    private fun commitSpaceWithCorrection() {
        val connection = currentInputConnection ?: return
        // End an email address with a normal space; skip autocorrection,
        // dictionary learning and double-space-to-period conversion.
        if (isEmailAddressContext()) {
            connection.commitText(" ", 1)
            return
        }

        val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()

        // Do not accumulate spaces. A second consecutive space becomes ". ".
        if (before.endsWith(" ")) {
            if (autoPunctuationSpaceEnabled && before.length >= 2 && !before[before.length - 2].isWhitespace()) {
                val previous = before[before.length - 2]
                if (previous in ".?!") return

                connection.deleteSurroundingText(1, 0)
                connection.commitText(". ", 1)
            }
            return
        }

        val word = getCurrentWord()

        if (autoCorrectionEnabled && word.isNotEmpty() &&
            personalWords.none { it.equals(word, ignoreCase = true) }) {
            // Cautious mode: only correct a small allow-list of clear typos.
            // Colloquialisms and stylistic variants remain suggestions, never auto-replacements.
            val safeAutoCorrectionKeys = setOf(
                "превет", "приветт", "спосибо", "спасибоо", "пожалуста",
                "пажалуйста", "севодня", "сигодня", "завтро", "чево",
                "чтобыы", "здравствуйтее", "извените", "извеняюсь",
                "потаму", "пачему", "сабака", "teh", "adn", "recieve",
                "becouse", "definately", "seperate", "thier", "wierd",
                "freind", "langauge", "wich"
            )
            val normalizedWord = word.lowercase(Locale.ROOT)
            val corrected = if (normalizedWord in safeAutoCorrectionKeys) {
                corrections[normalizedWord]
            } else null

            if (corrected != null) {
                val result = if (word.first().isUpperCase()) {
                    corrected.replaceFirstChar { it.uppercase() }
                } else {
                    corrected
                }

                connection.deleteSurroundingText(word.length, 0)
                connection.commitText(result, 1)
            } else {
                rememberUnknownWord(word)
            }
        } else if (word.isNotEmpty()) {
            // Learn completed words even when autocorrection is disabled.
            rememberUnknownWord(word)
        }

        connection.commitText(" ", 1)
    }

    /**
     * Returns true while the cursor is inside an email address.
     * Android email fields are detected by inputType. In ordinary text fields
     * we also detect an address token containing '@', e.g. ivan@gmail.com.
     */
    private fun isEmailAddressContext(): Boolean {
        val inputType = currentInputEditorInfo?.inputType ?: 0
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        if (inputClass == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
             variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        ) {
            return true
        }

        val before = currentInputConnection
            ?.getTextBeforeCursor(256, 0)
            ?.toString()
            .orEmpty()
            .trimEnd()

        val currentToken = before.takeLastWhile { !it.isWhitespace() }
        return currentToken.contains('@')
    }

    private fun commitPunctuation(symbol: String) {
        val connection = currentInputConnection ?: return
        val emailContext = isEmailAddressContext()

        // Do not learn parts of an email address as ordinary dictionary words.
        if (!emailContext) {
            rememberUnknownWord(getCurrentWord())
        }

        val before = connection.getTextBeforeCursor(1, 0)?.toString().orEmpty()

        // Replace a space before punctuation.
        if (before == " ") {
            connection.deleteSurroundingText(1, 0)
        }

        // A dot in an email address must not insert a trailing space.
        if (autoPunctuationSpaceEnabled && !emailContext) {
            connection.commitText("$symbol ", 1)
        } else {
            connection.commitText(symbol, 1)
        }
    }

    private fun shouldCapitalizeNext(): Boolean {
        if (!autoCapitalizationEnabled || isEmailAddressContext()) return false

        val before = currentInputConnection
            ?.getTextBeforeCursor(64, 0)
            ?.toString()
            .orEmpty()

        val trimmed = before.trimEnd()
        return trimmed.isEmpty() || trimmed.last() in ".?!\n"
    }

    private fun displayLetter(letter: String): String {
        return if (shift || capsLock || shouldCapitalizeNext()) {
            letter.uppercase()
        } else {
            letter
        }
    }

    private fun handleShift() {
        val now = SystemClock.uptimeMillis()

        if (now - lastShiftTap < 450) {
            capsLock = !capsLock
            shift = false
        } else {
            shift = !shift
        }

        lastShiftTap = now
        refresh()
    }

    private fun showEmoji(root: LinearLayout) {
        val emojis = listOf(
            listOf("😀","😂","🤣","😊","😍","🥰","😎"),
            listOf("😭","😡","🤔","🙄","😘","🤗","😴"),
            listOf("❤️","💙","💜","🔥","👍","👏","🙏"),
            listOf("🎉","✨","⭐","🌹","☀️","🚀","💯")
        )

        root.addView(createLabel("EMOJI"))

        emojis.forEach { keys ->
            val row = newRow()

            keys.forEach { emoji ->
                addKey(row, emoji, 1f) {
                    currentInputConnection?.commitText(emoji, 1)
                    isEmoji = false
                    isSymbols = false
                    refresh()
                }
            }

            root.addView(row)
        }

        val row = newRow()

        addKey(row, "ABC", 1f) {
            isEmoji = false
            refresh()
        }

        addKey(
            row,
            "⌫",
            1f,
            violetColor,
            repeatAction = { deleteBeforeCursor() }
        ) {
            deleteBeforeCursor()
            isEmoji = false
            refresh()
        }

        root.addView(row)
    }

    private fun showClipboard() {
        captureCurrentClipboard()
        val root = keyboardRoot ?: return
        root.removeAllViews()
        root.addView(createLabel("БУФЕР ОБМЕНА"))

        val controls = newRow()
        addKey(controls, "Очистить всё", 1f, violetColor, keyHeight = 34) {
            clearClipboardHistory()
            isEmoji = false
            isSymbols = false
            isAI = false
            isSettings = false
            refresh()
        }
        addKey(controls, "Назад", 1f, keyHeight = 34) { isSettings = false; refresh() }
        root.addView(controls)

        if (clipboardHistory.isEmpty()) {
            root.addView(createLabel("История пока пуста"))
            return
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var tileRow: LinearLayout? = null
        clipboardHistory.forEachIndexed { index, item ->
            if (index % 2 == 0) {
                tileRow = newRow()
                content.addView(tileRow)
            }
            val preview = item.replace("\n", " ").take(58)
            addTile(tileRow!!, "${index + 1}. $preview", "Нажми, чтобы вставить", false) {
                currentInputConnection?.commitText(item, 1)
                isEmoji = false
                isSymbols = false
                isAI = false
                isSettings = false
                refresh()
                Toast.makeText(this, "Текст вставлен", Toast.LENGTH_SHORT).show()
            }
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun clearClipboardHistory() {
        clipboardHistory.clear()
        saveClipboardHistory()
        var cleared = false
        try {
            val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
            cleared = true
        } catch (_: Exception) {
            cleared = false
        }
        showClipboard()
        Toast.makeText(
            this,
            if (cleared) "История и системный буфер очищены" else "История очищена; системный буфер недоступен",
            Toast.LENGTH_LONG
        ).show()
    }

    @Suppress("DEPRECATION")
    private fun showSettings() {
        val root = keyboardRoot ?: return
        root.removeAllViews()
        root.addView(createSettingsLabel("НАСТРОЙКИ"))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(createSettingsLabel("ФУНКЦИИ"))

        data class Toggle(val title: String, val enabled: Boolean, val action: () -> Unit)
        val toggles = listOf(
            Toggle("Автокоррекция", autoCorrectionEnabled) { autoCorrectionEnabled = !autoCorrectionEnabled },
            Toggle("Пробелы после знаков", autoPunctuationSpaceEnabled) { autoPunctuationSpaceEnabled = !autoPunctuationSpaceEnabled },
            Toggle("Автозаглавная", autoCapitalizationEnabled) { autoCapitalizationEnabled = !autoCapitalizationEnabled }
        )
        var toggleRow: LinearLayout? = null
        toggles.forEachIndexed { index, item ->
            if (index % 3 == 0) {
                toggleRow = newRow()
                content.addView(toggleRow)
            }
            addTile(toggleRow!!, item.title, if (item.enabled) "ВКЛ" else "ВЫКЛ", item.enabled) {
                item.action()
                saveKeyboardSettings()
                showSettings()
            }
        }

        content.addView(createSettingsLabel("ТЕМА КЛАВИАТУРЫ"))
        val themes = listOf(
            Triple("Klavio Neon", "klavio_neon", Color.rgb(20, 43, 82)),
            Triple("Серая", "gray", Color.rgb(119, 124, 133)),
            Triple("Тёмно-серая", "dark_gray", Color.rgb(47, 52, 61)),
            Triple("Синяя", "blue", Color.rgb(20, 62, 112)),
            Triple("Нежно-синяя", "light_blue", Color.rgb(190, 220, 242)),
            Triple("Светло-зелёная", "light_green", Color.rgb(196, 222, 199)),
            Triple("Стеклянная", "glass", Color.rgb(95, 130, 170)),
            Triple("🎄 Новый год", "new_year", Color.rgb(17, 49, 82)),
        )
        var themeRow: LinearLayout? = null
        themes.forEachIndexed { index, theme ->
            if (index % 3 == 0) {
                themeRow = newRow()
                content.addView(themeRow)
            }
            addThemeTile(themeRow!!, theme.first, theme.second, theme.third)
        }

        content.addView(createSettingsLabel("О ПРИЛОЖЕНИИ"))
        val appInfo = try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (_: Exception) {
            null
        }
        val versionName = appInfo?.versionName?.takeIf { it.isNotBlank() } ?: "не указана"
        val versionCode = if (appInfo == null) "—" else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            appInfo.versionCode.toString()
        }
        content.addView(createSettingsLabel("Anton Keyboard  •  версия $versionName  •  сборка $versionCode"))

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val back = newRow()
        addKey(back, "Готово", 1f, accentColor, keyHeight = 34) {
            isSettings = false
            refresh()
        }
        root.addView(back)
    }

    private fun createSettingsLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
    }

    private fun addTile(row: LinearLayout, title: String, subtitle: String, selected: Boolean, action: () -> Unit) {
        val tile = Button(this).apply {
            text = "$title\n$subtitle"
            textSize = 7f
            isAllCaps = false
            setTextColor(if (selected) Color.WHITE else textColorForTheme())
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = rounded(if (selected) violetColor else keyColor, 7)
            elevation = dp(1).toFloat()
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        row.addView(tile, params)
    }

    private fun addThemeTile(row: LinearLayout, label: String, theme: String, swatch: Int) {
        val selected = selectedTheme == theme
        val tile = Button(this).apply {
            text = if (selected) "✓  $label" else label
            textSize = 7f
            isAllCaps = false
            setTextColor(if (theme == "light_blue" || theme == "light_green" || theme == "gray") Color.rgb(25, 32, 42) else Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = 0
            minimumHeight = 0
            background = rounded(if (theme == "glass") Color.argb(185, 95, 130, 170) else swatch, 7)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            setOnClickListener { saveTheme(theme) }
        }
        row.addView(tile, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        })
    }

    private fun textColorForTheme(): Int {
        return if (selectedTheme == "light_blue" || selectedTheme == "light_green" || selectedTheme == "gray") Color.rgb(25, 32, 42) else Color.WHITE
    }

    private fun showAI(root: LinearLayout) {
        root.addView(createLabel("✦ ПОМОЩНИК"))

        val info = TextView(this).apply {
            text = "Твой интеллектуальный помощник.\n\nДля работы этих функций потребуется защищённый сервер."
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(18))
            background = rounded(keyColor, 16)
        }

        root.addView(info)

        listOf(
            "✦ Исправить текст",
            "✦ Перефразировать",
            "✦ Сократить текст",
            "✦ Перевести текст"
        ).forEach { label ->
            val row = newRow()

            addKey(row, label, 1f, violetColor) {
                Toast.makeText(
                    this,
                    "Эта функция появится в следующем обновлении",
                    Toast.LENGTH_SHORT
                ).show()
                isAI = false
                isSymbols = false
                isEmoji = false
                isSettings = false
                refresh()
            }

            root.addView(row)
        }

        val row = newRow()

        addKey(row, "Назад к клавиатуре", 1f, accentColor) {
            isAI = false
            isSettings = false
            refresh()
        }

        root.addView(row)
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
    }

    private fun newRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun addKey(
        row: LinearLayout,
        label: String,
        weight: Float,
        color: Int = keyColor,
        keyHeight: Int = 44,
        repeatAction: (() -> Unit)? = null,
        longPressAction: (() -> Unit)? = null,
        action: () -> Unit
    ) {
        // Keep the main glyph in its own centered TextView. Secondary symbols
        // are an independent top-right label, so they cannot shift the letter baseline.
        val keyFrame = FrameLayout(this)
        val button = Button(this).apply {
            text = label
            textSize = when {
                label.length == 1 && label[0].isLetter() -> 23f
                label.length > 6 -> 10f
                else -> 16f
            }
            gravity = Gravity.CENTER
            includeFontPadding = false
            isAllCaps = false
            setTextColor(if (color == violetColor || color == accentColor) Color.WHITE else textColorForTheme())
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
            background = when (selectedTheme) {
                "new_year" -> festiveKeyBackground(color)
                "klavio_neon" -> neonKeyBackground(color)
                else -> rounded(color, 8)
            }
            elevation = dp(3).toFloat()
            if (selectedTheme == "klavio_neon" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                outlineAmbientShadowColor = Color.rgb(20, 170, 255)
                outlineSpotShadowColor = Color.rgb(35, 205, 255)
            }
            minHeight = 0
            minimumHeight = 0
            isFocusable = false
            isSoundEffectsEnabled = false
            setPadding(dp(1), 0, dp(1), 0)
            stateListAnimator = null

            if (repeatAction == null && longPressAction == null) {
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            applyNeonKeyPressEffect(view, color, true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                            applyNeonKeyPressEffect(view, color, false)
                        }
                    }
                    false
                }
                setOnClickListener { action() }
            } else if (repeatAction == null) {
                val hapticView = this
                val handler = Handler(Looper.getMainLooper())
                var longPressed = false
                val longPressTask = Runnable {
                    longPressed = true
                    hapticView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    longPressAction?.invoke()
                }
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            longPressed = false
                            hapticView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            applyNeonKeyPressEffect(hapticView, color, true)
                            handler.postDelayed(longPressTask, 450L)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            handler.removeCallbacks(longPressTask)
                            applyNeonKeyPressEffect(hapticView, color, false)
                            if (!longPressed) action()
                            true
                        }
                        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                            handler.removeCallbacks(longPressTask)
                            applyNeonKeyPressEffect(hapticView, color, false)
                            true
                        }
                        else -> true
                    }
                }
            } else {
                val hapticView = this
                val handler = Handler(Looper.getMainLooper())
                var repeating = false
                val repeatTask = object : Runnable {
                    override fun run() {
                        repeating = true
                        repeatAction()
                        handler.postDelayed(this, 55L)
                    }
                }
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            repeating = false
                            hapticView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            applyNeonKeyPressEffect(hapticView, color, true)
                            handler.postDelayed(repeatTask, 350L)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            handler.removeCallbacks(repeatTask)
                            applyNeonKeyPressEffect(hapticView, color, false)
                            if (!repeating) action()
                            true
                        }
                        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                            handler.removeCallbacks(repeatTask)
                            applyNeonKeyPressEffect(hapticView, color, false)
                            true
                        }
                        else -> true
                    }
                }
            }
        }
        keyFrame.addView(button, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        if (label.length == 1 && label[0].isLetter() && !isSymbols) {
            val secondary = secondarySymbol(label.lowercase(Locale.ROOT))
            if (secondary.isNotEmpty()) {
                val secondaryView = TextView(this).apply {
                    text = secondary
                    textSize = 9f
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    setTextColor(if (selectedTheme == "new_year") Color.rgb(220, 239, 255) else textColorForTheme())
                    setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                    isClickable = false
                    isFocusable = false
                }
                val secondaryParams = FrameLayout.LayoutParams(dp(14), dp(13), Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(3)
                    marginEnd = dp(4)
                }
                keyFrame.addView(secondaryView, secondaryParams)
            }
        }

        val params = LinearLayout.LayoutParams(0, dp(keyHeight), weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        row.addView(keyFrame, params)
    }

    private fun secondarySymbol(letter: String): String {
        val symbols = mapOf(
            "й" to "1", "ц" to "2", "у" to "3", "к" to "4", "е" to "5",
            "н" to "6", "г" to "7", "ш" to "8", "щ" to "9", "з" to "0",
            "х" to "-", "ф" to "@", "ы" to "#", "в" to "₽", "а" to "_",
            "п" to "&", "р" to "-", "о" to "+", "л" to "(", "д" to ")",
            "ж" to "/", "э" to "~", "я" to "*", "ч" to "\"", "с" to "€",
            "м" to "£", "и" to "¥", "т" to "?", "ь" to "!", "б" to "%", "ю" to ":"
        )
        return symbols[letter] ?: ""
    }

    private fun neonKeyBackground(color: Int, pressed: Boolean = false): GradientDrawable {
        val top = if (pressed) Color.rgb(65, 205, 255) else lighten(color, 1.42f)
        val middle = if (pressed) Color.rgb(28, 112, 205) else color
        val bottom = if (pressed) Color.rgb(12, 48, 112) else darken(color, 0.68f)

        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(top, middle, bottom)
        ).apply {
            cornerRadius = dp(10).toFloat()
            setStroke(
                dp(if (pressed) 2 else 1),
                if (pressed) Color.rgb(145, 240, 255)
                else Color.argb(235, 55, 215, 255)
            )
        }
    }

    private fun applyNeonKeyPressEffect(view: View, keyColor: Int, pressed: Boolean) {
        if (selectedTheme != "klavio_neon") return

        view.background = neonKeyBackground(keyColor, pressed)
        view.animate().cancel()
        view.animate()
            .scaleX(if (pressed) 0.95f else 1f)
            .scaleY(if (pressed) 0.95f else 1f)
            .alpha(if (pressed) 0.96f else 1f)
            .setDuration(if (pressed) 55L else 105L)
            .start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.translationZ = if (pressed) dp(7).toFloat() else dp(2).toFloat()
        }
    }

    private fun festiveKeyBackground(color: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                lighten(color, 1.22f),
                color,
                darken(color, 0.78f)
            )
        ).apply {
            cornerRadius = dp(9).toFloat()
            setStroke(dp(1), Color.argb(150, 137, 211, 255))
        }
    }

    private fun lighten(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceAtMost(255),
        (Color.green(color) * factor).toInt().coerceAtMost(255),
        (Color.blue(color) * factor).toInt().coerceAtMost(255)
    )

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt(),
        (Color.green(color) * factor).toInt(),
        (Color.blue(color) * factor).toInt()
    )

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun refresh() {
        keyboardRoot?.let { renderKeyboard(it) }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
