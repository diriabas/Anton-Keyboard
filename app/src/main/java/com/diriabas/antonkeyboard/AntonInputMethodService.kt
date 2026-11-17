
package com.diriabas.antonkeyboard

import android.inputmethodservice.InputMethodService
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import org.json.JSONArray

class AntonInputMethodService : InputMethodService() {

    private var isRussian = false
    private var isSymbols = false
    private var isEmoji = false
    private var isAI = false
    private var isSettings = false

    private var shift = false
    private var capsLock = false
    private var lastShiftTap = 0L

    private var keyboardRoot: LinearLayout? = null

    private val clipboardHistory = mutableListOf<String>()
    private val maxClipboardItems = 20

    private val personalWords = mutableListOf<String>()
    private var autoCorrectionEnabled = true
    private var autoPunctuationSpaceEnabled = true
    private var autoCapitalizationEnabled = true

    private val backgroundColor = Color.rgb(14, 17, 29)
    private val keyColor = Color.rgb(34, 40, 59)
    private val accentColor = Color.rgb(67, 205, 255)
    private val violetColor = Color.rgb(153, 111, 255)

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
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(28))
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
            view.setPadding(dp(5), dp(5), dp(5), dp(28) + navigationBarBottom)
            insets
        }
        root.requestApplyInsets()

        keyboardRoot = root
        renderKeyboard(root)
        return root
    }

    private fun loadKeyboardSettings() {
        val prefs = getSharedPreferences("anton_keyboard", Context.MODE_PRIVATE)
        autoCorrectionEnabled = prefs.getBoolean("auto_correction", true)
        autoPunctuationSpaceEnabled = prefs.getBoolean("auto_punctuation_space", true)
        autoCapitalizationEnabled = prefs.getBoolean("auto_capitalization", true)
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
        val row = newRow()

        if (suggestions.isEmpty()) {
            row.minimumHeight = dp(20)
        } else {
            suggestions.forEach { suggestion ->
                addKey(row, suggestion, 1f, keyColor) {
                    replaceCurrentWord(suggestion)
                    refresh()
                }
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

        root.addView(createLabel("📋 CLIPBOARD HISTORY"))

        val controls = newRow()

        addKey(controls, "Очистить историю", 2f, violetColor) {
            clearClipboardHistory()
        }

        addKey(controls, "Назад", 1f) {
            refresh()
        }

        root.addView(controls)

        if (clipboardHistory.isEmpty()) {
            root.addView(createLabel("История пока пуста"))
            return
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
        }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        clipboardHistory.forEachIndexed { index, item ->
            val row = newRow()
            val preview = item.replace("\n", " ").take(55)

            addKey(
                row,
                "${index + 1}. $preview",
                1f,
                keyColor
            ) {
                currentInputConnection?.commitText(item, 1)

                Toast.makeText(
                    this,
                    "Текст вставлен",
                    Toast.LENGTH_SHORT
                ).show()
            }

            list.addView(row)
        }

        scrollView.addView(list)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun clearClipboardHistory() {
        clipboardHistory.clear()
        saveClipboardHistory()
        showClipboard()
    }

    private fun showSettings() {
        val root = keyboardRoot ?: return
        root.removeAllViews()
        root.addView(createLabel("⚙ НАСТРОЙКИ"))

        addSettingRow(root, "Автокоррекция", autoCorrectionEnabled) {
            autoCorrectionEnabled = !autoCorrectionEnabled
            saveKeyboardSettings()
            showSettings()
        }
        addSettingRow(root, "Пробел после знаков и двойной пробел", autoPunctuationSpaceEnabled) {
            autoPunctuationSpaceEnabled = !autoPunctuationSpaceEnabled
            saveKeyboardSettings()
            showSettings()
        }
        addSettingRow(root, "Автозаглавная буква", autoCapitalizationEnabled) {
            autoCapitalizationEnabled = !autoCapitalizationEnabled
            saveKeyboardSettings()
            showSettings()
        }

        root.addView(createLabel("МОЙ СЛОВАРЬ"))
        root.addView(createLabel("Сначала напечатай слово в поле сообщения, затем нажми кнопку ниже"))

        val addRow = newRow()
        addKey(addRow, "Добавить последнее слово из текста", 1f, violetColor) {
            val before = currentInputConnection
                ?.getTextBeforeCursor(80, 0)
                ?.toString()
                .orEmpty()
                .trimEnd()
            val word = Regex("[А-Яа-яЁёA-Za-z-]+$").find(before)?.value.orEmpty()
            addPersonalWord(word)
        }
        root.addView(addRow)

        if (personalWords.isEmpty()) {
            root.addView(createLabel("Пока нет собственных слов"))
        } else {
            root.addView(createLabel("Нажми на слово, чтобы удалить его"))
            val scroll = ScrollView(this)
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            personalWords.toList().forEach { word ->
                val row = newRow()
                addKey(row, "✕  $word", 1f, keyColor) {
                    personalWords.remove(word)
                    savePersonalWords()
                    showSettings()
                }
                list.addView(row)
            }
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        val back = newRow()
        addKey(back, "Готово", 1f, accentColor) {
            isSettings = false
            refresh()
        }
        root.addView(back)
    }

    private fun addSettingRow(root: LinearLayout, title: String, enabled: Boolean, action: () -> Unit) {
        val row = newRow()
        addKey(row, title, 3f, keyColor, action = action)
        addKey(row, if (enabled) "ВКЛ" else "ВЫКЛ", 1f, if (enabled) violetColor else keyColor, action = action)
        root.addView(row)
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
        keyHeight: Int = 42,
        repeatAction: (() -> Unit)? = null,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = rounded(color, 12)
            elevation = dp(2).toFloat()
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(2), 0, dp(2), 0)

            if (repeatAction == null) {
                setOnClickListener { action() }
            } else {
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

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), accentColor)
        }
    }

    private fun refresh() {
        keyboardRoot?.let { renderKeyboard(it) }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
