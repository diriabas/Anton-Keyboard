
package com.diriabas.antonkeyboard

import android.inputmethodservice.InputMethodService
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class AntonInputMethodService : InputMethodService() {

    private var isRussian = false
    private var isSymbols = false
    private var isEmoji = false
    private var isAI = false

    private var shift = false
    private var capsLock = false
    private var lastShiftTap = 0L

    private var keyboardRoot: LinearLayout? = null

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

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(9), dp(5), dp(8))
            setBackgroundColor(backgroundColor)
        }

        keyboardRoot = root
        renderKeyboard(root)
        return root
    }

    private fun renderKeyboard(root: LinearLayout) {
        root.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = TextView(this).apply {
            text = "ANTON  ✦  AI"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            gravity = Gravity.CENTER_VERTICAL
        }

        val language = TextView(this).apply {
            text = if (isRussian) "RU" else "EN"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(violetColor, 12)
        }

        header.addView(logo, LinearLayout.LayoutParams(0, dp(35), 1f))
        header.addView(language)
        root.addView(header)

        val toolbar = newRow()

        addKey(toolbar, "😊", 1f) {
            isEmoji = !isEmoji
            isAI = false
            isSymbols = false
            refresh()
        }

        addKey(toolbar, "📋", 1f) {
            isEmoji = false
            isAI = false
            showClipboard()
        }

        addKey(toolbar, "✦ AI", 1.2f, violetColor) {
            isAI = !isAI
            isEmoji = false
            isSymbols = false
            refresh()
        }

        addKey(toolbar, "🌐", 1f) {
            isRussian = !isRussian
            isSymbols = false
            isEmoji = false
            isAI = false
            refresh()
        }

        root.addView(toolbar)

        when {
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
                    if (shift || capsLock) violetColor else keyColor
                ) {
                    handleShift()
                }
            }

            keys.forEach { key ->
                addKey(row, displayLetter(key), 1f) {
                    currentInputConnection?.commitText(displayLetter(key), 1)

                    if (shift && !capsLock) {
                        shift = false
                    }

                    refresh()
                }
            }

            if (index == 2) {
                addKey(row, "⌫", 1.3f, violetColor) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
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
            currentInputConnection?.commitText(",", 1)
            refresh()
        }

        addKey(bottom, "Пробел", 4f, keyColor) {
            commitSpaceWithCorrection()
            refresh()
        }

        addKey(bottom, ".", 0.8f) {
            currentInputConnection?.commitText(".", 1)
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
            val hint = TextView(this).apply {
                text = "ANTON  ✦  SMART TYPING"
                textSize = 11f
                setTextColor(accentColor)
                gravity = Gravity.CENTER
            }

            row.addView(
                hint,
                LinearLayout.LayoutParams(0, dp(38), 1f)
            )
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

        corrections[normalized]?.let {
            results.add(it)
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

        return results
            .take(3)
            .map { suggestion ->
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

    private fun commitSpaceWithCorrection() {
        val connection = currentInputConnection ?: return
        val word = getCurrentWord()

        if (word.isNotEmpty()) {
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

    private fun displayLetter(letter: String): String {
        return if (shift || capsLock) {
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

        addKey(row, "⌫", 1f, violetColor) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        root.addView(row)
    }

    private fun showClipboard() {
        val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        val clip = if (manager.hasPrimaryClip()) {
            manager.primaryClip
        } else {
            null
        }

        val text = clip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()

        val root = keyboardRoot ?: return
        root.removeAllViews()

        root.addView(createLabel("CLIPBOARD"))

        if (text.isNullOrEmpty()) {
            root.addView(createLabel("Буфер обмена пуст"))
        } else {
            val preview = TextView(this).apply {
                this.text = text.take(300)
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(keyColor, 14)
            }

            root.addView(preview)

            val row = newRow()

            addKey(row, "Вставить", 2f, accentColor) {
                currentInputConnection?.commitText(text, 1)
            }

            root.addView(row)
        }

        val row = newRow()

        addKey(row, "Назад", 1f, violetColor) {
            refresh()
        }

        root.addView(row)
    }

    private fun showAI(root: LinearLayout) {
        root.addView(createLabel("✦ ANTON AI ASSISTANT"))

        val info = TextView(this).apply {
            text = "Твой интеллектуальный помощник.\n\nДля подключения настоящего AI требуется защищённый сервер."
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
                    "AI-сервер будет подключён на следующем этапе",
                    Toast.LENGTH_SHORT
                ).show()
            }

            root.addView(row)
        }

        val row = newRow()

        addKey(row, "Назад к клавиатуре", 1f, accentColor) {
            isAI = false
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
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            textSize = if (label == "Пробел") 13f else 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = rounded(color, 12)
            elevation = dp(2).toFloat()
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { action() }
        }

        val params = LinearLayout.LayoutParams(
            0,
            dp(42),
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
