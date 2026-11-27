package com.pannulabs.smarttrade

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.pannulabs.smarttrade.ui.money
import com.pannulabs.smarttrade.ui.price
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Draws a branded PannuLabs SmartTrade PnL card for one position and opens Android's share sheet. */
object PnlShare {
    const val W = 1080
    const val H = 1350
    private const val M = 72f // side margin

    private const val BG = 0xFF0B0F17.toInt()
    private const val TEXT = 0xFFE8ECF4.toInt()
    private const val MUTED = 0xFF8A94A8.toInt()
    private const val FAINT = 0xFF5B6478.toInt()
    private const val BORDER = 0xFF242E44.toInt()
    private const val GREEN = 0xFF18D885.toInt()
    private const val RED = 0xFFF05463.toInt()

    fun share(ctx: Context, p: Position, roiPct: Double) {
        val bmp = render(ctx, p.symbol, p.side, p.unrealized, roiPct, p.entry, p.mark, System.currentTimeMillis())
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "pnl.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("PnL", uri)
        ctx.startActivity(Intent.createChooser(send, "Share PnL"))
    }

    private fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

    fun render(
        ctx: Context, symbol: String, side: String, pnl: Double, roiPct: Double,
        entry: Double, mark: Double, timeMs: Long,
    ): Bitmap {
        val bold = ResourcesCompat.getFont(ctx, R.font.inter_bold) ?: Typeface.DEFAULT_BOLD
        val semi = ResourcesCompat.getFont(ctx, R.font.inter_semibold) ?: Typeface.DEFAULT_BOLD
        val medium = ResourcesCompat.getFont(ctx, R.font.inter_medium) ?: Typeface.DEFAULT
        val regular = ResourcesCompat.getFont(ctx, R.font.inter_regular) ?: Typeface.DEFAULT
        val up = pnl >= 0
        val pnlColor = if (up) GREEN else RED
        val sideColor = if (side == "LONG") GREEN else RED

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)

        // Soft glow behind the result, green for profit, red for loss.
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(W * 0.82f, H * 0.42f, 760f, intArrayOf(alpha(pnlColor, 46), alpha(pnlColor, 0)), null,
                Shader.TileMode.CLAMP)
        }.also { c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), it) }

        fun text(s: String, x: Float, y: Float, tf: Typeface, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT): Float {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color; textAlign = align }
            c.drawText(s, x, y, p)
            return p.measureText(s)
        }

        // Header: logo + two-tone wordmark
        BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)?.let { logo ->
            c.drawBitmap(logo, null, Rect(M.toInt(), 72, M.toInt() + 120, 192), Paint(Paint.FILTER_BITMAP_FLAG))
            logo.recycle()
        }
        val wx = M + 144
        var x = wx
        listOf("Pannu" to TEXT, "Labs " to GREEN, "Smart" to TEXT, "Trade" to GREEN).forEach { (part, col) ->
            x += text(part, x, 132f, bold, 52f, col)
        }
        text("Automated crypto trading", wx, 178f, regular, 30f, MUTED)

        // Pair + side pill
        val symW = text(symbol, M, 380f, bold, 76f, TEXT)
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = semi; textSize = 34f }
        val sideW = pill.measureText(side)
        val px = M + symW + 28f
        val rect = RectF(px, 326f, px + sideW + 48f, 384f)
        c.drawRoundRect(rect, 29f, 29f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(sideColor, 46) })
        text(side, px + 24f, 367f, semi, 34f, sideColor)

        // ROI (big) and PnL
        text("ROI", M, 520f, medium, 36f, MUTED)
        val roiText = String.format(Locale.US, "%+.2f%%", roiPct)
        var roiSize = 140f
        val probe = Paint().apply { typeface = bold; textSize = roiSize }
        while (probe.measureText(roiText) > W - 2 * M && roiSize > 90f) { roiSize -= 6f; probe.textSize = roiSize }
        text(roiText, M - 4f, 680f, bold, roiSize, pnlColor)
        text("PNL (USDT)", M, 790f, medium, 36f, MUTED)
        text(money(pnl, true), M, 890f, bold, 84f, pnlColor)

        // Divider + prices
        c.drawRect(M, 1010f, W - M, 1012f, Paint().apply { color = BORDER })
        val col2 = W / 2f + 20f
        text("Entry Price", M, 1080f, medium, 34f, MUTED)
        text(price(entry), M, 1150f, semi, 56f, TEXT)
        text("Mark Price", col2, 1080f, medium, 34f, MUTED)
        text(price(mark), col2, 1150f, semi, 56f, TEXT)

        // Footer
        val stamp = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(timeMs))
        text(stamp, M, H - 72f, regular, 30f, FAINT)
        text("www.pannulabs.com", W - M, H - 72f, semi, 32f, GREEN, Paint.Align.RIGHT)
        return bmp
    }
}
