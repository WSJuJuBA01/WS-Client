package com.wsclient

import android.content.Context
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.net.Uri

/** Persistent appearance preferences. Wallpaper URIs are persisted by the activity. */
object Customization {
    private const val PREFS = "ws_settings"
    private const val KEY_PRESET = "appearance_preset"
    private const val KEY_PASTEL = "pastel_theme"
    private const val KEY_DARK = "appearance_dark"
    private const val KEY_ACCENT = "appearance_accent"
    private const val KEY_WALLPAPER = "appearance_wallpaper"
    private const val KEY_DIM = "appearance_wallpaper_dim"
    private const val KEY_CARD_ALPHA = "appearance_card_alpha"

    data class Palette(
        /** Например "midnight:dark" или "midnight:light". */
        val id: String,
        val title: String,
        val background: Int,
        val surface: Int,
        val foreground: Int,
        val muted: Int,
        val accent: Int,
        val isLight: Boolean
    ) {
        /** Название темы без варианта: midnight, ocean, forest, pastel, paper. */
        val family: String get() = id.substringBefore(':')
    }

    /** Тема с двумя вариантами оформления: тёмным и светлым. */
    private data class Theme(
        val id: String,
        val dark: Palette,
        val light: Palette,
        /** Какой вариант был у темы изначально (для старых настроек). */
        val defaultDark: Boolean
    )

    private val themes = listOf(
        Theme(
            "midnight",
            dark = Palette(
                "midnight:dark", "Midnight",
                Color.rgb(15, 20, 32),
                Color.rgb(29, 39, 57),
                Color.rgb(242, 245, 250),
                Color.rgb(138, 152, 178),
                Color.rgb(58, 123, 200),
                false
            ),
            light = Palette(
                "midnight:light", "Midnight",
                Color.rgb(233, 238, 247),
                Color.WHITE,
                Color.rgb(21, 28, 43),
                Color.rgb(98, 111, 136),
                Color.rgb(47, 109, 196),
                true
            ),
            defaultDark = true
        ),
        Theme(
            "ocean",
            dark = Palette(
                "ocean:dark", "Ocean",
                Color.rgb(8, 25, 39),
                Color.rgb(17, 43, 61),
                Color.rgb(235, 247, 255),
                Color.rgb(145, 181, 199),
                Color.rgb(44, 166, 200),
                false
            ),
            light = Palette(
                "ocean:light", "Ocean",
                Color.rgb(228, 243, 250),
                Color.WHITE,
                Color.rgb(10, 38, 56),
                Color.rgb(87, 117, 133),
                Color.rgb(14, 140, 178),
                true
            ),
            defaultDark = true
        ),
        Theme(
            "forest",
            dark = Palette(
                "forest:dark", "Forest",
                Color.rgb(13, 27, 22),
                Color.rgb(25, 45, 36),
                Color.rgb(238, 248, 240),
                Color.rgb(151, 179, 159),
                Color.rgb(75, 173, 119),
                false
            ),
            light = Palette(
                "forest:light", "Forest",
                Color.rgb(231, 244, 235),
                Color.WHITE,
                Color.rgb(17, 42, 30),
                Color.rgb(92, 120, 102),
                Color.rgb(40, 142, 90),
                true
            ),
            defaultDark = true
        ),
        Theme(
            "pastel",
            dark = Palette(
                "pastel:dark", "Pastel",
                Color.rgb(30, 22, 38),
                Color.rgb(48, 37, 60),
                Color.rgb(247, 238, 250),
                Color.rgb(176, 157, 189),
                Color.rgb(190, 150, 222),
                false
            ),
            light = Palette(
                "pastel:light", "Pastel",
                Color.rgb(248, 238, 249),
                Color.WHITE,
                Color.rgb(55, 39, 67),
                Color.rgb(133, 113, 145),
                Color.rgb(151, 104, 181),
                true
            ),
            defaultDark = false
        ),
        Theme(
            "paper",
            dark = Palette(
                "paper:dark", "Paper",
                Color.rgb(18, 20, 26),
                Color.rgb(31, 35, 44),
                Color.rgb(236, 239, 245),
                Color.rgb(140, 148, 163),
                Color.rgb(104, 148, 235),
                false
            ),
            light = Palette(
                "paper:light", "Paper",
                Color.rgb(245, 246, 249),
                Color.WHITE,
                Color.rgb(31, 38, 50),
                Color.rgb(111, 122, 139),
                Color.rgb(63, 105, 190),
                true
            ),
            defaultDark = false
        )
    )

    /** Список тем для выбора, показанных в текущем (тёмном или светлом) варианте. */
    fun presets(context: Context): List<Palette> {
        val dark = isDark(context)
        return themes.map { if (dark) it.dark else it.light }
    }

    fun palette(context: Context): Palette {
        val theme = currentTheme(context)

        return if (isDark(context)) theme.dark else theme.light
    }

    private fun currentTheme(context: Context): Theme {
        val p = prefs(context)
        val saved = p.getString(KEY_PRESET, null)
        val id = saved ?: if (
            p.getBoolean(KEY_PASTEL, false)
        ) "pastel" else "midnight"

        return themes.firstOrNull { it.id == id }
            ?: themes.first()
    }

    /** true — выбран тёмный вариант оформления. */
    fun isDark(context: Context): Boolean {
        val p = prefs(context)
        val theme = currentTheme(context)

        return if (p.contains(KEY_DARK)) {
            p.getBoolean(KEY_DARK, theme.defaultDark)
        } else {
            theme.defaultDark
        }
    }

    fun setDark(context: Context, dark: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_DARK, dark)
            .putBoolean(KEY_PASTEL, !dark)
            .apply()
    }

    fun toggleDark(context: Context) {
        setDark(context, !isDark(context))
    }

    /** Выбирает тему (midnight, ocean…), сохраняя текущий тёмный/светлый вариант. */
    fun setPreset(context: Context, id: String) {
        require(themes.any { it.id == id }) {
            "Unknown appearance preset"
        }

        // Вариант считаем до записи, чтобы он не сбросился
        // на вариант по умолчанию у новой темы.
        val dark = isDark(context)

        prefs(context).edit()
            .putString(KEY_PRESET, id)
            .putBoolean(KEY_DARK, dark)
            .putBoolean(KEY_PASTEL, !dark)
            .apply()
    }

    fun isPastel(context: Context): Boolean =
        palette(context).isLight

    /** Переключает светлый/тёмный вариант текущей темы. */
    fun togglePastel(context: Context) {
        toggleDark(context)
    }

    fun accent(context: Context): Int {
        val raw = prefs(context)
            .getString(KEY_ACCENT, null)
            ?: return palette(context).accent

        return runCatching {
            Color.parseColor(raw)
        }.getOrDefault(palette(context).accent)
    }

    fun setAccent(context: Context, color: Int) {
        prefs(context).edit()
            .putString(
                KEY_ACCENT,
                String.format("#%06X", 0xFFFFFF and color)
            )
            .apply()
    }

    fun clearAccent(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACCENT)
            .apply()
    }

    fun wallpaper(context: Context): Uri? =
        prefs(context).getString(KEY_WALLPAPER, null)?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }

    fun setWallpaper(context: Context, uri: Uri?) {
        prefs(context).edit().apply {
            if (uri == null) {
                remove(KEY_WALLPAPER)
            } else {
                putString(KEY_WALLPAPER, uri.toString())
            }
        }.apply()
    }

    fun dim(context: Context): Int =
        prefs(context).getInt(KEY_DIM, 48).coerceIn(0, 90)

    fun setDim(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_DIM, value.coerceIn(0, 90))
            .apply()
    }

    fun cardAlpha(context: Context): Int =
        prefs(context)
            .getInt(KEY_CARD_ALPHA, 94)
            .coerceIn(45, 100)

    fun setCardAlpha(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_CARD_ALPHA, value.coerceIn(45, 100))
            .apply()
    }

    /** Applies a downsampled wallpaper to avoid decoding multi-megapixel images at full size. */
    fun applyWallpaper(context: Context, target: View) {
        val uri = wallpaper(context) ?: return
        val width = context.resources.displayMetrics.widthPixels
            .coerceAtLeast(1)
        val height = context.resources.displayMetrics.heightPixels
            .coerceAtLeast(1)

        val bitmap = runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            var sample = 1

            while (
                bounds.outWidth / (sample * 2) >= width &&
                bounds.outHeight / (sample * 2) >= height
            ) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
            }

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull() ?: return

        val image = BitmapDrawable(
            context.resources,
            bitmap
        ).apply {
            gravity = android.view.Gravity.FILL
        }

        val overlay = ColorDrawable(
            Color.argb(
                dim(context) * 255 / 100,
                0,
                0,
                0
            )
        )

        target.background = LayerDrawable(
            arrayOf(image, overlay)
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
}
