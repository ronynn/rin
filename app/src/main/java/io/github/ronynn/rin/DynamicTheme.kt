package io.github.ronynn.rin

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color as CColor
import android.graphics.Color as AColor

data class WallpaperTheme(
    val bg: CColor,
    val surface: CColor,
    val border: CColor,
    val text: CColor,
    val accent: CColor,
    val accentTxt: CColor,
    val gray: CColor,
    val source: String
)

object DynamicTheme {

    fun extract(context: Context): WallpaperTheme? {
        val (accentInt, source) = extractAccent(context) ?: return null
        return build(accentInt, source)
    }

    fun toRinColors(t: WallpaperTheme): RinColors = RinColors(
        bg = t.bg,
        fg = t.text,
        accent = t.accent,
        dim = t.gray,
        panel = t.surface,
        listHover = t.border,
        border = t.border,
        isLight = false
    )

    private fun extractAccent(context: Context): Pair<Int, String>? {
        extractFromWallpaperColors(context)?.let { return it }
        extractFromBitmap(context)?.let { return it }
        Log.w("RinDynamic", "No wallpaper color found — using static dynamic preset")
        return null
    }

    private fun extractFromWallpaperColors(context: Context): Pair<Int, String>? {
        if (Build.VERSION.SDK_INT < 27) return null
        return try {
            val wc = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
            wc.primaryColor?.let { return it.toArgb() to "primary" }
            wc.secondaryColor?.let { return it.toArgb() to "secondary" }
            wc.tertiaryColor?.let { return it.toArgb() to "tertiary" }
            null
        } catch (_: Throwable) { null }
    }

    @Suppress("DEPRECATION")
    private fun extractFromBitmap(context: Context): Pair<Int, String>? {
        return try {
            val drawable = WallpaperManager.getInstance(context).drawable ?: return null
            val w = 64
            val h = 64
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)

            val buckets = IntArray(64)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val px = bmp.getPixel(x, y)
                    val a = (px ushr 24) and 0xFF
                    if (a < 128) continue
                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    val b = px and 0xFF
                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    if (max - min < 30) continue
                    if (max < 40 || min > 220) continue
                    val idx = ((r shr 6) shl 4) or ((g shr 6) shl 2) or (b shr 6)
                    buckets[idx]++
                }
            }
            var bestIdx = -1
            var bestCount = 0
            for (i in 0 until 64) {
                if (buckets[i] > bestCount) {
                    bestCount = buckets[i]
                    bestIdx = i
                }
            }
            bmp.recycle()
            if (bestIdx < 0) return null

            val r = ((bestIdx shr 4) and 3) * 64 + 32
            val g = ((bestIdx shr 2) and 3) * 64 + 32
            val b = (bestIdx and 3) * 64 + 32
            AColor.rgb(r, g, b) to "bitmap"
        } catch (_: Throwable) { null }
    }

    private fun build(accentInt: Int, source: String): WallpaperTheme {
        val hsv = FloatArray(3)
        AColor.colorToHSV(accentInt, hsv)

        val aS = hsv[1].coerceIn(0.40f, 0.55f)
        val aV = hsv[2].coerceIn(0.78f, 0.90f)
        val accent = AColor.HSVToColor(floatArrayOf(hsv[0], aS, aV))

        val h = hsv[0]
        return WallpaperTheme(
            bg = CColor(AColor.HSVToColor(floatArrayOf(h, 0.11f, 0.09f))),
            surface = CColor(AColor.HSVToColor(floatArrayOf(h, 0.13f, 0.14f))),
            border = CColor(AColor.HSVToColor(floatArrayOf(h, 0.20f, 0.38f))),
            text = CColor(AColor.HSVToColor(floatArrayOf(h, 0.05f, 0.89f))),
            accent = CColor(accent),
            accentTxt = CColor(AColor.HSVToColor(floatArrayOf(h, 0.40f, 0.14f))),
            gray = CColor(AColor.HSVToColor(floatArrayOf(h, 0.09f, 0.52f))),
            source = source
        )
    }
}