package com.example.kakascalendar.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File

/** Everything that goes on the shareable brag card. */
data class BragCard(
    val name: String,
    val avatar: String,
    val streak: Int,
    val best: Int,
    val total: Int,
    val level: Int,
    val levelTitle: String,
    val trophies: Int,
    val trophiesTotal: Int,
    val colors: List<Int>    // ARGB gradient colors
)

object ShareCard {

    fun render(card: BragCard): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), card.colors.toIntArray(), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        // soft glow circles
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
        c.drawCircle(w * 0.85f, h * 0.12f, 260f, glow)
        c.drawCircle(w * 0.1f, h * 0.88f, 320f, glow)

        fun text(s: String, size: Float, y: Float, bold: Boolean = false, alpha: Int = 255) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(alpha, 255, 255, 255)
                textSize = size
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            c.drawText(s, w / 2f, y, p)
        }

        text("KAKASCALENDAR", 44f, 120f, bold = true, alpha = 220)
        text(card.avatar, 150f, 330f)
        text(card.name, 70f, 440f, bold = true)
        text("Level ${card.level} · ${card.levelTitle}", 42f, 505f, alpha = 230)

        text("🔥", 120f, 720f)
        text("${card.streak}", 240f, 960f, bold = true)
        text("day streak", 56f, 1035f, alpha = 235)

        // stat strip
        val strip = RectF(90f, 1110f, w - 90f, 1250f)
        c.drawRoundRect(strip, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
        val cols = listOf("👑 ${card.best}d" to "best", "💩 ${card.total}" to "total", "🏆 ${card.trophies}/${card.trophiesTotal}" to "trophies")
        val colW = strip.width() / cols.size
        cols.forEachIndexed { i, (v, label) ->
            val cx = strip.left + colW * i + colW / 2
            val pv = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE; textSize = 46f; textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val pl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xDDFFFFFF.toInt(); textSize = 30f; textAlign = Paint.Align.CENTER
            }
            c.drawText(v, cx, strip.top + 70f, pv)
            c.drawText(label, cx, strip.top + 112f, pl)
        }
        text("Stronger. Smarter. Better.", 34f, 1310f, alpha = 200)
        return bmp
    }

    /** Renders the card and opens the system share sheet. */
    fun share(ctx: Context, card: BragCard) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "kakascalendar-streak.png")
        file.outputStream().use { render(card).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "🔥 ${card.streak}-day streak on Kakascalendar. Beat that.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share your greatness").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
