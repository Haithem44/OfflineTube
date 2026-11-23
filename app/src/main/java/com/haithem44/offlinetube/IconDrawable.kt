package com.haithem44.offlinetube

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

class IconDrawable(private val kind: Int, initial: Int) : Drawable() {
    companion object {
        const val HOME = 0; const val SEARCH = 1; const val PLAY = 2; const val REFRESH = 3
        const val FOLDER = 4; const val TRASH = 5; const val BACK = 6; const val LOCK = 7
        const val UNLOCK = 8; const val PAUSE = 9; const val PLAY_FILLED = 10; const val EXPAND = 11
        const val ROTATE = 12; const val PIP = 13; const val COMPRESS = 14; const val COMMENT = 15
        const val DOWNLOAD = 16
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND; color = initial
    }

    fun setIconColor(c: Int) { paint.color = c; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds; val size = minOf(b.width(), b.height()).toFloat()
        if (size <= 0f) return
        val s = size / 24f
        canvas.save()
        canvas.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f)
        canvas.scale(s, s); paint.strokeWidth = 2f; paint.style = Paint.Style.STROKE
        val p = Path()
        when (kind) {
            HOME -> {
                p.moveTo(3f, 11f); p.lineTo(12f, 3f); p.lineTo(21f, 11f)
                p.moveTo(5f, 10f); p.lineTo(5f, 20f); p.lineTo(10f, 20f)
                p.lineTo(10f, 14f); p.lineTo(14f, 14f); p.lineTo(14f, 20f)
                p.lineTo(19f, 20f); p.lineTo(19f, 10f)
            }
            SEARCH -> { canvas.drawCircle(10.5f, 10.5f, 6.5f, paint); p.moveTo(15.3f, 15.3f); p.lineTo(21f, 21f) }
            PLAY -> { p.moveTo(8f, 5f); p.lineTo(19f, 12f); p.lineTo(8f, 19f); p.close() }
            REFRESH -> { canvas.drawArc(RectF(4f, 4f, 20f, 20f), 25f, 300f, false, paint); p.moveTo(18.5f, 2.5f); p.lineTo(18.5f, 7.5f); p.lineTo(13.5f, 7.5f) }
            FOLDER -> { p.moveTo(3f, 6f); p.lineTo(9f, 6f); p.lineTo(11f, 8f); p.lineTo(21f, 8f); p.lineTo(21f, 19f); p.lineTo(3f, 19f); p.close() }
            TRASH -> {
                p.moveTo(3.5f, 6f); p.lineTo(20.5f, 6f)
                p.moveTo(9f, 6f); p.lineTo(9f, 4.5f); p.quadTo(9f, 3.5f, 10f, 3.5f); p.lineTo(14f, 3.5f); p.quadTo(15f, 3.5f, 15f, 4.5f); p.lineTo(15f, 6f)
                p.moveTo(5.5f, 6f); p.lineTo(6.4f, 19.2f); p.quadTo(6.5f, 20.5f, 7.8f, 20.5f); p.lineTo(16.2f, 20.5f); p.quadTo(17.5f, 20.5f, 17.6f, 19.2f); p.lineTo(18.5f, 6f)
                p.moveTo(10f, 10f); p.lineTo(10f, 16.5f)
                p.moveTo(14f, 10f); p.lineTo(14f, 16.5f)
            }
            BACK -> { p.moveTo(20f, 12f); p.lineTo(4f, 12f); p.moveTo(10f, 6f); p.lineTo(4f, 12f); p.lineTo(10f, 18f) }
            LOCK -> { canvas.drawRoundRect(RectF(5f, 11f, 19f, 21f), 2f, 2f, paint); p.moveTo(8f, 11f); p.lineTo(8f, 8f); p.arcTo(RectF(8f, 4f, 16f, 12f), 180f, 180f); p.lineTo(16f, 11f) }
            UNLOCK -> { canvas.drawRoundRect(RectF(5f, 11f, 19f, 21f), 2f, 2f, paint); p.moveTo(8f, 11f); p.lineTo(8f, 8f); p.arcTo(RectF(8f, 4f, 16f, 12f), 180f, 180f) }
            PAUSE -> { paint.style = Paint.Style.FILL; canvas.drawRoundRect(RectF(6f, 4f, 10.5f, 20f), 1.5f, 1.5f, paint); canvas.drawRoundRect(RectF(13.5f, 4f, 18f, 20f), 1.5f, 1.5f, paint); paint.style = Paint.Style.STROKE }
            PLAY_FILLED -> { paint.style = Paint.Style.FILL; p.moveTo(7f, 4.5f); p.lineTo(19f, 12f); p.lineTo(7f, 19.5f); p.close(); canvas.drawPath(p, paint); p.reset(); paint.style = Paint.Style.STROKE }
            EXPAND -> { p.moveTo(4f, 9f); p.lineTo(4f, 4f); p.lineTo(9f, 4f); p.moveTo(15f, 4f); p.lineTo(20f, 4f); p.lineTo(20f, 9f); p.moveTo(20f, 15f); p.lineTo(20f, 20f); p.lineTo(15f, 20f); p.moveTo(9f, 20f); p.lineTo(4f, 20f); p.lineTo(4f, 15f) }
            ROTATE -> { canvas.drawRoundRect(RectF(3f, 6f, 21f, 18f), 2.5f, 2.5f, paint); p.moveTo(18f, 10f); p.lineTo(18f, 14f) }
            PIP -> { canvas.drawRoundRect(RectF(3f, 5f, 21f, 19f), 2f, 2f, paint); paint.style = Paint.Style.FILL; canvas.drawRoundRect(RectF(12f, 11f, 19f, 17f), 1f, 1f, paint); paint.style = Paint.Style.STROKE }
            COMPRESS -> { p.moveTo(9f, 4f); p.lineTo(9f, 9f); p.lineTo(4f, 9f); p.moveTo(15f, 4f); p.lineTo(15f, 9f); p.lineTo(20f, 9f); p.moveTo(20f, 15f); p.lineTo(15f, 15f); p.lineTo(15f, 20f); p.moveTo(4f, 15f); p.lineTo(9f, 15f); p.lineTo(9f, 20f) }
            COMMENT -> { canvas.drawRoundRect(RectF(3f, 4f, 21f, 17f), 3f, 3f, paint); p.moveTo(8f, 17f); p.lineTo(8f, 21f); p.lineTo(12.5f, 17f) }
            DOWNLOAD -> { p.moveTo(12f, 4f); p.lineTo(12f, 15f); p.moveTo(7f, 10.5f); p.lineTo(12f, 15.5f); p.lineTo(17f, 10.5f); p.moveTo(5f, 19.5f); p.lineTo(19f, 19.5f) }
        }
        canvas.drawPath(p, paint); canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
