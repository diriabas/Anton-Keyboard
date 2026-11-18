
package com.diriabas.antonkeyboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO = 501
    }

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
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(enableButton)
        root.addView(chooseButton)
        setContentView(root)

        requestMicrophonePermissionIfNeeded()
    }

    private fun requestMicrophonePermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == REQUEST_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED

            Toast.makeText(
                this,
                if (granted) {
                    "Доступ к микрофону разрешён"
                } else {
                    "Для голосового набора нужен доступ к микрофону"
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
