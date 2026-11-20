package com.wsclient

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class AppearanceActivity : Activity() {
    private lateinit var root: LinearLayout
    private val palette get() = Customization.palette(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
            setBackgroundColor(palette.background)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(root) }
        root.addView(label("Внешний вид", 27, true))
        root.addView(label("Темы, акцент, обои и прозрачность карточек", 13, false).also { it.setPadding(0, dp(5), 0, dp(18)) })
        val autoConnect = android.widget.CheckBox(this).apply {
            text = "Автоподключение при запуске WS Client"
            textSize = 13f
            setTextColor(palette.foreground)
            isChecked = AutoConnect.enabled(this@AppearanceActivity)
            setOnCheckedChangeListener { _, checked -> AutoConnect.setEnabled(this@AppearanceActivity, checked) }
        }
        root.addView(autoConnect)
        root.addView(
            action(
                if (palette.isLight) "Переключить на тёмную тему" else "Переключить на светлую тему",
                Customization.accent(this),
                Color.WHITE
            ) {
                Customization.setPreset(
                    this,
                    if (palette.isLight) "midnight" else "pastel"
                )
                buildUi()
            }
        )
        root.addView(space(10))
        root.addView(label("ТЕМА", 11, true))
        Customization.presets.forEach { preset ->
            root.addView(action("${if (palette.id == preset.id) "✓  " else ""}${preset.title}", preset.surface, preset.foreground) {
                Customization.setPreset(this, preset.id)
                buildUi()
            })
            root.addView(space(7))
        }
        root.addView(space(10))
        root.addView(label("АКЦЕНТНЫЙ ЦВЕТ", 11, true))
        root.addView(action("Выбрать цвет по HEX", palette.surface, palette.foreground) { editAccent() })
        root.addView(action("Сбросить акцент темы", palette.surface, palette.foreground) {
            Customization.clearAccent(this); buildUi()
        })
        root.addView(space(12))
        root.addView(label("ОБОИ", 11, true))
        root.addView(action("Выбрать изображение из галереи", palette.surface, palette.foreground) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, REQUEST_WALLPAPER)
        })
        root.addView(action("Убрать обои", palette.surface, palette.foreground) {
            Customization.setWallpaper(this, null); buildUi()
        })
        root.addView(space(6))
        root.addView(label("Затемнение обоев: ${Customization.dim(this)}%", 13, false))
        root.addView(slider(0, 90, Customization.dim(this)) { Customization.setDim(this, it) })
        root.addView(space(10))
        root.addView(label("Прозрачность карточек: ${Customization.cardAlpha(this)}%", 13, false))
        root.addView(slider(45, 100, Customization.cardAlpha(this)) { Customization.setCardAlpha(this, it) })
        root.addView(space(16))
        root.addView(action("ГОТОВО", Customization.accent(this), Color.WHITE) { finish() })
        setContentView(scroll)
        Ui.prepareWindow(this, palette); Ui.padWithInsets(root)
        applyWallpaperPreview()
    }

    private fun editAccent() {
        val input = EditText(this).apply {
            hint = "#RRGGBB"
            setSingleLine(true)
            setText(String.format("#%06X", 0xFFFFFF and Customization.accent(this@AppearanceActivity)))
        }
        AlertDialog.Builder(this).setTitle("Акцентный цвет").setView(input)
            .setPositiveButton("Применить") { _, _ ->
                val color = runCatching { Color.parseColor(input.text.toString().trim()) }.getOrNull()
                if (color == null) Toast.makeText(this, "Нужен HEX-цвет, например #6688FF", Toast.LENGTH_LONG).show()
                else { Customization.setAccent(this, color); buildUi() }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun slider(min: Int, max: Int, value: Int, onChanged: (Int) -> Unit): SeekBar = SeekBar(this).apply {
        this.max = max - min
        progress = value - min
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onChanged(progress + min) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun applyWallpaperPreview() {
        Customization.applyWallpaper(this, root)
    }

    @Deprecated("Deprecated Android callback retained for minSdk 24")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_WALLPAPER || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Customization.setWallpaper(this, uri)
            buildUi()
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось сохранить доступ к изображению: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun label(textValue: String, size: Int, bold: Boolean) = TextView(this).apply {
        text = textValue; textSize = size.toFloat(); setTextColor(palette.foreground)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private fun action(textValue: String, bg: Int, fg: Int, click: () -> Unit) = TextView(this).apply {
        text = textValue; textSize = 14f; gravity = Gravity.CENTER_VERTICAL
        setTextColor(fg); setPadding(dp(15), dp(15), dp(15), dp(15))
        background = android.graphics.drawable.GradientDrawable().apply { setColor(bg); cornerRadius = dp(14).toFloat() }
        isClickable = true; isFocusable = true; setOnClickListener { click() }
    }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    companion object { private const val REQUEST_WALLPAPER = 812 }
}
