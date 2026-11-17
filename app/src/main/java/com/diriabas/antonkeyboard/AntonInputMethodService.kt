
package com.diriabas.antonkeyboard

import android.inputmethodservice.InputMethodService
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import org.json.JSONArray

class AntonInputMethodService : InputMethodService() {

    private var isRussian = true
    private var isSymbols = false
    private var isEmoji = false
    private var isAI = false
    private var isSettings = false

    private var shift = false
    private var capsLock = false
    private var lastShiftTap = 0L

    private var keyboardRoot: LinearLayout? = null
    private var winterEffectsView: View? = null

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
        "спосибо" to "спасибо",
        "пожалуста" to "пожалуйста",
        "здравствуйтее" to "здравствуйте",
        "teh" to "the",
        "adn" to "and",
        "recieve" to "receive",
        "becouse" to "because"
    )

    private val russianWords = listOf(
        "привет", "приветик", "приветствую",
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

    override fun onCreate() {
        super.onCreate()
        loadClipboardHistory()
        loadKeyboardSettings()
        loadPersonalWords()
        loadTheme()
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
            setPadding(dp(5), 0, dp(5), dp(28))
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
            view.setPadding(dp(5), 0, dp(5), dp(28) + navigationBarBottom)
            insets
        }

        keyboardRoot = root
        renderKeyboard(root)

        val container = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor)
            clipChildren = false
            clipToPadding = false
            addView(
                root,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            winterEffectsView = WinterEffectsView(this@AntonInputMethodService).also {
                it.visibility = if (selectedTheme == "winter") View.VISIBLE else View.GONE
                addView(
                    it,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
        container.requestApplyInsets()
        return container
    }

    private fun loadKeyboardSettings() {
        val prefs = getSharedPreferences("anton_keyboard", Context.MODE_PRIVATE)
        autoCorrectionEnabled = prefs.getBoolean("auto_correction", true)
        autoPunctuationSpaceEnabled = prefs.getBoolean("auto_punctuation_space", true)
        autoCapitalizationEnabled = prefs.getBoolean("auto_capitalization", true)
    }

    private fun loadTheme() {
        selectedTheme = getSharedPreferences("anton_keyboard", Context.MODE_PRIVATE)
            .getString("keyboard_theme", "dark_gray") ?: "dark_gray"
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
        winterEffectsView?.visibility = if (theme == "winter") View.VISIBLE else View.GONE
        winterEffectsView?.invalidate()
        window?.window?.navigationBarColor = backgroundColor
        refresh()
    }

    private fun applyThemeColors() {
        when (selectedTheme) {
            "gray" -> {
                backgroundColor = Color.rgb(92, 96, 104)
                keyColor = Color.rgb(119, 124, 133)
                accentColor = Color.rgb(225, 229, 235)
                violetColor = Color.rgb(157, 165, 178)
            }
            "dark_gray" -> {
                backgroundColor = Color.rgb(25, 28, 34)
                keyColor = Color.rgb(47, 52, 61)
                accentColor = Color.rgb(185, 194, 207)
                violetColor = Color.rgb(105, 115, 132)
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
            "winter" -> {
                backgroundColor = Color.rgb(7, 20, 48)
                keyColor = Color.rgb(19, 55, 105)
                accentColor = Color.rgb(225, 244, 255)
                violetColor = Color.rgb(80, 157, 235)
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

        // Compact shortcut strip: no branding or language badge.
        val toolbar = newRow()

        addKey(toolbar, "😊", 1f, keyColor, keyHeight = 30) {
            isEmoji = !isEmoji
            isAI = false
            isSettings = false
            isSymbols = false
            refresh()
        }

        addKey(toolbar, "📋", 1f, keyColor, keyHeight = 30) {
            isEmoji = false
            isAI = false
            isSettings = false
            showClipboard()
        }

        addKey(toolbar, "✦", 1f, violetColor, keyHeight = 30) {
            isAI = !isAI
            isEmoji = false
            isSettings = false
            isSymbols = false
            refresh()
        }

        addKey(toolbar, "🌐", 1f, keyColor, keyHeight = 30) {
            isRussian = !isRussian
            isSymbols = false
            isEmoji = false
            isAI = false
            isSettings = false
            refresh()
        }

        addKey(toolbar, "⚙", 0.72f, keyColor, keyHeight = 30) {
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

    private fun showLetters(root: LinearLayout) {
        if (!isSymbols) {
            showSuggestions(root)
        }

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
                addKey(row, displayLetter(key), 1f) {
                    if (isSymbols && key in listOf(",", ".", "!", "?", ";", ":")) {
                        commitPunctuation(key)
                    } else {
                        currentInputConnection?.commitText(displayLetter(key), 1)
                    }

                    if (shift && !capsLock) {
                        shift = false
                    }

                    refresh()
                }
            }

            if (index == 2) {
                addKey(
                    row,
                    "⌫",
                    1.3f,
                    violetColor,
                    repeatAction = { deleteBeforeCursor() }
                ) {
                    deleteBeforeCursor()
                    refresh()
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
            refresh()
        }

        addKey(bottom, "", 4f, keyColor) {
            commitSpaceWithCorrection()
            refresh()
        }

        addKey(bottom, ".", 0.8f) {
            commitPunctuation(".")
            refresh()
        }

        addKey(bottom, "↵", 1.2f, violetColor) {
            currentInputConnection?.commitText("\n", 1)
            refresh()
        }

        root.addView(bottom)
    }

    private fun showSuggestions(root: LinearLayout) {
        val word = getCurrentWord()
        val suggestions = getSuggestions(word)

        // Do not reserve an empty 20dp row: it creates a visible gap above the keys.
        if (suggestions.isEmpty()) return

        val row = newRow()
        suggestions.forEach { suggestion ->
            addKey(row, suggestion, 1f, keyColor) {
                replaceCurrentWord(suggestion)
                refresh()
            }
        }
        root.addView(row)
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
            russianWords
        } else {
            englishWords
        }

        val results = mutableListOf<String>()

        personalWords
            .filter { it.lowercase(Locale.ROOT).startsWith(normalized) && !it.equals(normalized, ignoreCase = true) }
            .forEach { if (it !in results) results.add(it) }

        if (autoCorrectionEnabled) {
            corrections[normalized]?.let { results.add(it) }
        }

        dictionary
            .filter {
                it.startsWith(normalized) &&
                !it.equals(normalized, ignoreCase = true)
            }
            .sortedBy { it.length }
            .forEach {
                if (it !in results) results.add(it)
            }

        return results.take(3).map { suggestion ->
            if (word.firstOrNull()?.isUpperCase() == true) {
                suggestion.replaceFirstChar { it.uppercase() }
            } else {
                suggestion
            }
        }
    }

    private fun replaceCurrentWord(suggestion: String) {
        val connection = currentInputConnection ?: return
        val word = getCurrentWord()

        if (word.isNotEmpty()) {
            connection.deleteSurroundingText(word.length, 0)
        }

        connection.commitText(suggestion, 1)
    }

    private fun deleteBeforeCursor() {
        val connection = currentInputConnection ?: return
        val selected = connectio
