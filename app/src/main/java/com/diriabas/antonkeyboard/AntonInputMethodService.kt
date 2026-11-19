
package com.diriabas.antonkeyboard

import android.inputmethodservice.InputMethodService
import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
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
    private var suggestionsRow: LinearLayout? = null

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
        selectedTheme = prefs.getString("keyboard_theme", "dark_gray") ?: "dark_gray"
        if (selectedTheme == "winter") {
            selectedTheme = "dark_gray"
            prefs.edit().putString("keyboard_theme", selectedTheme).apply()
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
        suggestionsRow = null

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

        if (!isSymbols) {
            val numberRow = newRow()
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { number ->
                addKey(numberRow, number, 1f, keyHeight = 43) {
                    currentInputConnection?.commitText(number, 1)
                    updateSuggestions()
                }
            }
            root.addView(numberRow)
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
                            updateSuggestions()
                        }
                    }
                ) {
                    if (isSymbols && key in listOf(",", ".", "!", "?", ";", ":")) {
                        commitPunctuation(key)
                    } else {
                        currentInputConnection?.commitText(displayLetter(key), 1)
                    }

                    if (shift && !capsLock) {
                        shift = false
                    }
                    updateSuggestions()
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
            refresh()
        }

        addKey(bottom, if (isRussian) "Русский" else "English", 4f, keyColor, keyHeight = 48) {
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
        suggestionsRow = row
        suggestions.forEach { suggestion ->
            addKey(row, suggestion, 1f, keyColor) {
                replaceCurrentWord(suggestion)
                updateSuggestions()
            }
        }
        root.addView(row)
    }

    // Update only the suggestion strip; rebuilding the full keyboard on every
    // keystroke can cause visible input lag on some devices.
    private fun updateSuggestions() {
        val root = keyboardRoot ?: return
        if (isSymbols || isEmoji || isAI || isSettings) return

        suggestionsRow?.let { oldRow ->
            root.removeView(oldRow)
            suggestionsRow = null
        }

        val word = getCurrentWord()
        val suggestions = getSuggestions(word)
        if (suggestions.isEmpty()) return

        val row = newRow()
        suggestionsRow = row
        suggestions.forEach { suggestion ->
            addKey(row, suggestion, 1f, keyColor) {
                replaceCurrentWord(suggestion)
                updateSuggestions()
            }
        }
        root.addView(row, minOf(1, root.childCount))
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
        val selected = connection.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            connection.commitText("", 1)
        } else {
            connection.deleteSurroundingText(1, 0)
        }
    }

    private fun commitSpaceWithCorrection() {
        val connection = currentInputConnection ?: return
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

        if (autoCorrectionEnabled && word.isNotEmpty()) {
            val corrected = corrections[word.lowercase(Locale.ROOT)]

            if (corrected != null) {
                val result = if (word.first().isUpperCase()) {
                    corrected.replaceFirstChar { it.uppercase() }
                } else {
                    corrected
                }

                connection.deleteSurroundingText(word.length, 0)
                connection.commitText(result, 1)
            }
        }

        connection.commitText(" ", 1)
    }

    private fun commitPunctuation(symbol: String) {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(1, 0)?.toString().orEmpty()

        // Replace a space before punctuation and add exactly one space after it.
        if (before == " ") {
            connection.deleteSurroundingText(1, 0)
        }

        if (autoPunctuationSpaceEnabled) {
            connection.commitText("$symbol ", 1)
        } else {
            connection.commitText(symbol, 1)
        }
    }

    private fun shouldCapitalizeNext(): Boolean {
        if (!autoCapitalizationEnabled) return false
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
        addKey(controls, "Очистить всё", 1f, violetColor, keyHeight = 34) { clearClipboardHistory() }
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

    private fun showSettings() {
        val root = keyboardRoot ?: return
        root.removeAllViews()
        root.addView(createLabel("НАСТРОЙКИ"))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(createLabel("ФУНКЦИИ"))

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

        content.addView(createLabel("ТЕМА КЛАВИАТУРЫ"))
        val themes = listOf(
            Triple("Серая", "gray", Color.rgb(119, 124, 133)),
            Triple("Тёмно-серая", "dark_gray", Color.rgb(47, 52, 61)),
            Triple("Синяя", "blue", Color.rgb(20, 62, 112)),
            Triple("Нежно-синяя", "light_blue", Color.rgb(190, 220, 242)),
            Triple("Светло-зелёная", "light_green", Color.rgb(196, 222, 199)),
            Triple("Стеклянная", "glass", Color.rgb(95, 130, 170)),
        )
        var themeRow: LinearLayout? = null
        themes.forEachIndexed { index, theme ->
            if (index % 3 == 0) {
                themeRow = newRow()
                content.addView(themeRow)
            }
            addThemeTile(themeRow!!, theme.first, theme.second, theme.third)
        }

        // Personal dictionary is intentionally hidden from Settings UI.
        // Existing personal words remain available to word suggestions.
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val back = newRow()
        addKey(back, "Готово", 1f, accentColor, keyHeight = 34) {
            isSettings = false
            refresh()
        }
        root.addView(back)
    }

    private fun addTile(row: LinearLayout, title: String, subtitle: String, selected: Boolean, action: () -> Unit) {
        val tile = Button(this).apply {
            text = "$title\n$subtitle"
            textSize = 9f
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
        val params = LinearLayout.LayoutParams(dp(58), dp(58)).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        row.addView(tile, params)
    }

    private fun addThemeTile(row: LinearLayout, label: String, theme: String, swatch: Int) {
        val selected = selectedTheme == theme
        val tile = Button(this).apply {
            text = if (selected) "✓  $label" else label
            textSize = 9f
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
        row.addView(tile, LinearLayout.LayoutParams(dp(58), dp(58)).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
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
        }
    }

    private fun addKey(
        row: LinearLayout,
        label: String,
        weight: Float,
        color: Int = keyColor,
        keyHeight: Int = 49,
        repeatAction: (() -> Unit)? = null,
        longPressAction: (() -> Unit)? = null,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            textSize = when {
                label.length == 1 && label[0].isLetter() -> 25f
                label.length > 6 -> 11f
                else -> 17f
            }
            isAllCaps = false
            if (label.length == 1 && label[0].isLetter() && !isSymbols) {
                val secondary = secondarySymbol(label.lowercase(Locale.ROOT))
                if (secondary.isNotEmpty()) {
                    val decorated = SpannableString(label + secondary)
                    decorated.setSpan(RelativeSizeSpan(0.42f), 1, decorated.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    decorated.setSpan(SuperscriptSpan(), 1, decorated.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text = decorated
                }
            }
            setTextColor(if (color == violetColor || color == accentColor) Color.WHITE else textColorForTheme())
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
            background = rounded(color, 8)
            elevation = dp(2).toFloat()
            minHeight = 0
            minimumHeight = 0
            isFocusable = false
            isSoundEffectsEnabled = false
            setPadding(dp(1), 0, dp(1), 0)

            if (repeatAction == null && longPressAction == null) {
                setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
                            handler.postDelayed(longPressTask, 450L)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            handler.removeCallbacks(longPressTask)
                            if (!longPressed) action()
                            true
                        }
                        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                            handler.removeCallbacks(longPressTask)
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
                            handler.postDelayed(repeatTask, 350L)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            handler.removeCallbacks(repeatTask)
                            if (!repeating) action()
                            true
                        }
                        MotionEvent.ACTION_CANCEL,
                        MotionEvent.ACTION_OUTSIDE -> {
                            handler.removeCallbacks(repeatTask)
                            true
                        }
                        else -> true
                    }
                }
            }
        }

        val params = LinearLayout.LayoutParams(
            0,
            dp(keyHeight),
            weight
        ).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }

        row.addView(button, params)
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
