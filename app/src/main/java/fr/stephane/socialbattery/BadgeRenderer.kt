package fr.stephane.socialbattery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb

/** Dessine le badge (sans l'heure) en image, pour le cadran de montre. */
object BadgeRenderer {
    fun render(level: Level, size: Int = 400): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(level.color.toArgb())

        val s = size / 200f          // même repère 200x200 que la maquette
        val cx = size / 2f
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, 900, false)
            letterSpacing = 0.05f
        }

        fun arcText(str: String, radius: Float, fontSize: Float, top: Boolean) {
            text.textSize = fontSize * s
            val r = radius * s
            val oval = RectF(cx - r, cx - r, cx + r, cx + r)
            // Haut : de gauche à droite par le haut. Bas : de gauche à droite par le bas.
            val path = Path().apply { addArc(oval, 180f, if (top) 180f else -180f) }
            val length = (Math.PI * r).toFloat()
            c.drawTextOnPath(str, path, (length - text.measureText(str)) / 2f, 0f, text)
        }

        arcText(level.title, 66f, 24f, top = true)
        arcText(level.tagline, 80f, 15f, top = false)

        val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 58 * s
            textAlign = Paint.Align.CENTER
        }
        c.drawText(level.emoji, cx, 112 * s, emoji)
        return bmp
    }
}
