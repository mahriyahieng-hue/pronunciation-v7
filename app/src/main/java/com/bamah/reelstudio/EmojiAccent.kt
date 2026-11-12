package com.bamah.reelstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Samples Android's own emoji glyph and caches its dominant saturated hue. */
object EmojiAccent {
    private val cache = LinkedHashMap<String, Int>(32, 0.75f, true)

    fun resolve(emoji: String, fallbackHex: String): Int {
        val fallback = runCatching { Color.parseColor(fallbackHex) }.getOrDefault(Color.rgb(233, 161, 123))
        if (emoji.isBlank()) return fallback
        val key = "$emoji|$fallbackHex"
        synchronized(cache) { cache[key]?.let { return it } }
        val resolved = sample(emoji, fallback)
        synchronized(cache) {
            cache[key] = resolved
            if (cache.size > 128) cache.remove(cache.keys.first())
        }
        return resolved
    }

    private fun sample(emoji: String, fallback: Int): Int {
        val bitmap = runCatching { Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return fallback
        return try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = 104f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT
            }
            canvas.drawText(emoji, 64f, 98f, paint)
            val bins = FloatArray(24)
            val hsv = FloatArray(3)
            for (y in 0 until 128 step 2) for (x in 0 until 128 step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) < 100) continue
                Color.colorToHSV(pixel, hsv)
                if (hsv[1] < 0.30f || hsv[2] < 0.22f || hsv[2] > 0.98f) continue
                val bin = ((hsv[0] / 360f) * bins.size).toInt().coerceIn(0, bins.lastIndex)
                bins[bin] += hsv[1] * (0.5f + hsv[2] * 0.5f)
            }
            val best = bins.indices.maxByOrNull { bins[it] } ?: return fallback
            if (bins[best] < 1.5f) return fallback
            Color.HSVToColor(floatArrayOf((best + 0.5f) * 360f / bins.size, 0.66f, 0.88f))
        } catch (_: Exception) {
            fallback
        } finally {
            bitmap.recycle()
        }
    }
}

fun emojiAccentColor(emoji: String, fallbackHex: String): Int = EmojiAccent.resolve(emoji, fallbackHex)
