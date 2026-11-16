
package com.diriabas.antonkeyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AntonInputMethodService : InputMethodService() {

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setBackgroundColor(Color.rgb(25, 27, 35))
        }

        val title = TextView(this).apply {
            text = "ANTON KEYBOARD  •  AI"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        root.addView(title)

        addRow(root, listOf("q","w","e","r","t","y","u","i","o","p"))
        addRow(root, listOf("a","s","d","f","g","h","j","k","l"))
        addRow(root, listOf("z","x","c","v","b","n","m","⌫"))
        addRow(root, listOf("?123", "🌐", "SPACE", "↵"))

        return root
    }

    private fun addRow(root: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (key in keys) {
            val button = Button(this).apply {
                text = if (key == "SPACE") " " else key
                textSize = 16f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(65, 70, 85))

                setOnClickListener {
                    when (key) {
                        "⌫" -> currentInputConnection
                            ?.deleteSurroundingText(1, 0)
                        "SPACE" -> currentInputConnection
                            ?.commitText(" ", 1)
                        "↵" -> currentInputConnection
                            ?.commitText("\n", 1)
                        "?123", "🌐" -> { }
                        else -> currentInputConnection
                            ?.commitText(key, 1)
                    }
                }
            }

            val params = LinearLayout.LayoutParams(
                0, dp(48), 1f
            ).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }

            row.addView(button, params)
        }

        root.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
