
package com.diriabas.antonkeyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AntonInputMethodService : InputMethodService() {

    private var isRussian = false
    private var isSymbols = false
    private var keyboardRoot: LinearLayout? = null

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setBackgroundColor(Color.rgb(22, 24, 32))
        }

        keyboardRoot = root
        renderKeyboard(root)
        return root
    }

    private fun renderKeyboard(root: LinearLayout) {
        root.removeAllViews()

        val title = TextView(this).apply {
            text = "ANTON KEYBOARD  •  ${if (isRussian) "RU" else "EN"}"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        root.addView(title)

        val rows = when {
            isSymbols -> listOf(
                listOf("1","2","3","4","5","6","7","8","9","0"),
                listOf("@","#","$","%","&","-","+","(",")","/"),
                listOf("*","\"","'",":",";","!","?","⌫")
            )
            isRussian -> listOf(
                listOf("й","ц","у","к","е","н","г","ш","щ","з","х"),
                listOf("ф","ы","в","а","п","р","о","л","д","ж","э"),
                listOf("я","ч","с","м","и","т","ь","б","ю","⌫")
            )
            else -> listOf(
                listOf("q","w","e","r","t","y","u","i","o","p"),
                listOf("a","s","d","f","g","h","j","k","l"),
                listOf("z","x","c","v","b","n","m","⌫")
            )
        }

        rows.forEach { keys -> addRow(root, keys) }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        addSpecialKey(bottom, "?123") {
            isSymbols = !isSymbols
            keyboardRoot?.let { renderKeyboard(it) }
        }

        addSpecialKey(bottom, "🌐") {
            isRussian = !isRussian
            isSymbols = false
            keyboardRoot?.let { renderKeyboard(it) }
        }

        addSpecialKey(bottom, "Пробел") {
            currentInputConnection?.commitText(" ", 1)
        }

        addSpecialKey(bottom, "↵") {
            currentInputConnection?.commitText("\n", 1)
        }

        root.addView(bottom)
    }

    private fun addRow(root: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        keys.forEach { key ->
            val button = createButton(key)

            button.setOnClickListener {
                if (key == "⌫") {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                } else {
                    currentInputConnection?.commitText(key, 1)
                }
            }

            row.addView(button, buttonParams())
        }

        root.addView(row)
    }

    private fun addSpecialKey(
        row: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = createButton(label)
        button.textSize = 13f
        button.setOnClickListener { action() }
        row.addView(button, buttonParams())
    }

    private fun createButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(65, 72, 90))
        }
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            dp(48),
            1f
        ).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
