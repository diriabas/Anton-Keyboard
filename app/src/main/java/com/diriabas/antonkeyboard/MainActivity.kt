package com.diriabas.antonkeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(14, 17, 29))
        }

        val title = TextView(this).apply {
            text = "✦  ANTON KEYBOARD"
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(67, 205, 255))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Настройка клавиатуры"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 28)
        }

        val enableButton = Button(this).apply {
            text = "Включить Anton Keyboard"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val chooseButton = Button(this).apply {
            text = "Выбрать клавиатуру"
            isAllCaps = false
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(enableButton)
        root.addView(chooseButton)
        setContentView(root)
    }
}
