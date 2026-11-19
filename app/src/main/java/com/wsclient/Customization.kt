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
    private const val KEY_ACCENT = "appearance_accent"
    private const val KEY_WALLPAPER = "appearance_wallpaper"
    private const val KEY_DIM = "appearance_wallpaper_dim"
    private const val KEY_CARD_ALPHA = "appearance_card_alpha"

    data class Palette(
        val id: String,
        val title: String,
        val background: Int,
        val surface: Int,
        val foreground: Int,
        val muted: Int,
        val accent: Int,
        val isLight: Boolean
    )

    val presets = listOf(
        Palette(
            "midnight", "Midnight",
            Color.rgb(13, 16, 24),
            Color.rgb(24, 29, 41),
            Color.rgb(242, 245, 250),
            Color.rgb(145, 157, 180),
            Color.rgb(76, 116, 245),
            false
        ),
        Palette(
            "ocean", "Ocean",
            Color.rgb(8, 25, 39),
            Color.rgb(17, 43, 61),
            Color.rgb(235, 247, 255),
            Color.rgb(145, 181, 199),
            Color.rgb(44, 166, 200),
            false
        ),
        Palette(
            "forest", "Forest",
            Color.rgb(13, 27, 22),
            Color.rgb(25, 45, 36),
            Color.rgb(238, 248, 240),
            Color.rgb(151, 179, 159),
            Color.rgb(75, 173, 119),
            false
        ),
        Palette(
            "pastel", "Pastel",
            Color.rgb(248, 238, 249),
            Color.WHITE,
            Color.rgb(55, 39, 67),
            Color.rgb(133, 113, 145),
            Color.rgb(151, 104, 181),
            true
        ),
        Palette(
            "paper", "Paper",
            Color.rgb(245, 246, 249),
            Color.WHITE,
            Color.rgb(31, 38, 50),
            Color.rgb(111, 122, 139),
            Color.rgb(63, 105, 190),
            true
        )
    )

    fun palette(context: Context): Palette {
        val p = prefs(context)
        val saved = p.getString(KEY_PRESET, null)
        val id = saved ?: if (
            p.getBoolean(KEY_PASTEL, false)
        ) "pastel" else "midnight"

        return presets.firstOrNull { it.id == id }
            ?: presets.first()
    }

    fun setPreset(context: Context, id: String) {
        require(presets.any { it.id == id }) {
            "Unknown appearance preset"
        }

        prefs(context).edit()
            .putString(KEY_PRESET, id)
            .putBoolean(
                KEY_PASTEL,
                presets.first { it.id == id }.isLight
            )
            .apply()
    }

    fun isPastel(context: Context): Boolean =
        palette(context).isLight

    fun togglePastel(context: Context) {
        setPreset(
            context,
            if (isPastel(context)) "midnight" else "pastel"
        )
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
