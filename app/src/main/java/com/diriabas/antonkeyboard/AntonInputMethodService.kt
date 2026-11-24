```kotlin
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

    private fun showSettings() {
        val root = keyboardRoot ?: return
        root.removeAllViews()
        root.addView(createSettingsLabel("НАСТРОЙКИ"))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(createSettingsLabel("ФУНКЦИИ"))

        data class Toggle(
            val title: String,
            val enabled: Boolean,
            val action: () -> Unit
        )

        val toggles = listOf(
            Toggle("Автокоррекция", autoCorrectionEnabled) {
                autoCorrectionEnabled = !autoCorrectionEnabled
            },
            Toggle("Пробелы после знаков", autoPunctuationSpaceEnabled) {
                autoPunctuationSpaceEnabled = !autoPunctuationSpaceEnabled
            },
            Toggle("Автозаглавная", autoCapitalizationEnabled) {
                autoCapitalizationEnabled = !autoCapitalizationEnabled
            }
        )

        var toggleRow: LinearLayout? = null
        toggles.forEachIndexed { index, item ->
            if (index % 3 == 0) {
                toggleRow = newRow()
                content.addView(toggleRow)
            }
            addTile(
                toggleRow!!,
                item.title,
                if (item.enabled) "ВКЛ" else "ВЫКЛ",
                item.enabled
            ) {
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
            Triple("🎄 Новый год", "new_year", Color.rgb(17, 49, 82))
        )

        var themeRow: LinearLayout? = null
        themes.forEachIndexed { index, theme ->
            if (index % 3 == 0) {
                themeRow = newRow()
                content.addView(themeRow)
            }
            addThemeTile(
                themeRow!!,
                theme.first,
                theme.second,
                theme.third
            )
        }

        content.addView(createSettingsLabel("ЛИЧНЫЙ СЛОВАРЬ"))

        val addWordRow = newRow()
        val wordInput = TextView(this).apply {
            text = "＋ Добавить слово"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(keyColor, 9)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                val input = android.widget.EditText(this@AntonInputMethodService)
                input.hint = "Введите слово"
                AlertDialog.Builder(this@AntonInputMethodService)
                    .setTitle("Личный словарь")
                    .setView(input)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Добавить") { _, _ ->
                        addPersonalWord(input.text.toString())
                    }
                    .show()
            }
        }
        addWordRow.addView(
            wordInput,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        )
        content.addView(addWordRow)

        if (personalWords.isNotEmpty()) {
            personalWords.take(30).forEach { word ->
                val wordRow = newRow()
                addKey(wordRow, word, 4f, keyColor) {
                    confirmRemovePersonalWord(word)
                }
                content.addView(wordRow)
            }
        }

        content.addView(createSettingsLabel("О ПРИЛОЖЕНИИ"))
        val appInfo = try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (_: Exception) {
            null
        }
        val versionName = appInfo?.versionName?.takeIf { it.isNotBlank() } ?: "не указана"
        val versionCode = if (appInfo == null) {
            "—"
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            appInfo.versionCode.toString()
        }

        content.addView(
            createSettingsLabel(
                "Anton Keyboard  •  версия $versionName  •  сборка $versionCode"
            )
        )

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val backRow = newRow()
        addKey(backRow, "НАЗАД", 1f, violetColor) {
            isSettings = false
            refresh()
        }
        root.addView(backRow)
    }

    private fun createSettingsLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(accentColor)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(8), dp(8), dp(5))
        }
    }

    private fun addTile(
        row: LinearLayout,
        title: String,
        value: String,
        enabled: Boolean,
        action: () -> Unit
    ) {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(5), dp(3), dp(5))
            background = rounded(
                if (enabled) violetColor else keyColor,
                9
            )
            setOnClickListener { action() }
        }

        tile.addView(TextView(this).apply {
            text = title
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })

        tile.addView(TextView(this).apply {
            text = value
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })

        row.addView(
            tile,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        )
    }

    private fun addThemeTile(
        row: LinearLayout,
        title: String,
        theme: String,
        color: Int
    ) {
        val tile = TextView(this).apply {
            text = if (selectedTheme == theme) "✓ $title" else title
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(color, 9)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnClickListener {
                saveTheme(theme)
                showSettings()
            }
        }

        row.addView(
            tile,
            LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        )
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

    private fun showLetters(root: LinearLayout) {
        val rows = when {
            isSymbols -> listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
                listOf("*", "\"", "'", ";", ":", "!", "?")
            )

            isRussian -> listOf(
                listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х"),
                listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
                listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "ё")
            )

            else -> listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m")
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
                    repeatAction = {
                        deleteBeforeCursor()
                        updateSuggestions()
                    }
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
        if (isEmailAddressContext()) return

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
                row,
                suggestion,
                1f,
                keyColor,
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
