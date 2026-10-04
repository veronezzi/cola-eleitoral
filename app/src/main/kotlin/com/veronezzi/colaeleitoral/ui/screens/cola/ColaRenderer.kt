package com.veronezzi.colaeleitoral.ui.screens.cola

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import kotlin.math.max

/** One vote of the cola, with every text already localized. [digits] null draws empty boxes. */
data class ColaLine(
    val label: String,
    val digits: String?,
    val digitCount: Int,
    val nameLine: String?,
)

/** Everything the cola shows, ready to draw (ARCHITECTURE.md 4.7). No photos, no QR code, no watermark. */
data class ColaContent(
    val title: String,
    val lines: List<ColaLine>,
    val footer: List<String>,
)

/**
 * Draws the cola on any [Canvas]: the PDF page of the print framework and the PNG to share use
 * the same code. Black on white, digits in large boxes (at least 20 pt on an A4 page), one row per
 * vote in urna order, the legal reminder and the source in the footer.
 */
class ColaRenderer {
    fun draw(canvas: Canvas, content: ColaContent, width: Float, height: Float) {
        val scale = width / REFERENCE_WIDTH
        val margin = 64f * scale
        canvas.drawColor(Color.WHITE)

        val titlePaint = textPaint(46f * scale, bold = true)
        val labelPaint = textPaint(34f * scale, bold = true)
        val namePaint = textPaint(30f * scale, bold = false)
        val digitPaint = textPaint(60f * scale, bold = true).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val footerPaint = textPaint(24f * scale, bold = false).apply { color = FOOTER_COLOR }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
            color = Color.BLACK
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1.5f * scale
            color = DIVIDER_COLOR
        }

        val contentWidth = width - 2 * margin
        var y = margin
        val title = layout(content.title, titlePaint, contentWidth.toInt(), maxLines = 3)
        canvas.withTranslation(margin, y) { title.draw(this) }
        y += title.height + 28f * scale

        val boxWidth = 76f * scale
        val boxHeight = 92f * scale
        val boxGap = 12f * scale
        content.lines.forEach { line ->
            val count = max(line.digitCount, line.digits?.length ?: 0)
            val blockWidth = count * boxWidth + (count - 1).coerceAtLeast(0) * boxGap
            val textWidth = (contentWidth - blockWidth - 24f * scale).toInt().coerceAtLeast(1)
            val label = layout(line.label, labelPaint, textWidth, maxLines = 2)
            val name = line.nameLine?.let { layout(it, namePaint, textWidth, maxLines = 2) }
            val textHeight = label.height + (name?.let { it.height + 6f * scale } ?: 0f)
            val rowHeight = max(boxHeight, textHeight)

            canvas.withTranslation(margin, y + (rowHeight - textHeight) / 2f) {
                label.draw(this)
                name?.let {
                    translate(0f, label.height + 6f * scale)
                    it.draw(this)
                }
            }
            val boxTop = y + (rowHeight - boxHeight) / 2f
            var x = width - margin - blockWidth
            repeat(count) { index ->
                canvas.drawRoundRect(RectF(x, boxTop, x + boxWidth, boxTop + boxHeight), 8f * scale, 8f * scale, boxPaint)
                line.digits?.getOrNull(index)?.let { digit ->
                    val baseline = boxTop + boxHeight / 2f - (digitPaint.descent() + digitPaint.ascent()) / 2f
                    canvas.drawText(digit.toString(), x + boxWidth / 2f, baseline, digitPaint)
                }
                x += boxWidth + boxGap
            }
            y += rowHeight + 14f * scale
            canvas.drawLine(margin, y, width - margin, y, dividerPaint)
            y += 14f * scale
        }

        val footer = layout(content.footer.joinToString("\n\n"), footerPaint, contentWidth.toInt(), maxLines = 12)
        val footerTop = max(y + 16f * scale, height - margin - footer.height)
        canvas.withTranslation(margin, footerTop) { footer.draw(this) }
    }

    /** PNG for sharing: 1080 x 1350 px (4:5, fits phone screens and chat apps). */
    fun renderBitmap(content: ColaContent, width: Int = IMAGE_WIDTH, height: Int = IMAGE_HEIGHT): Bitmap {
        val bitmap = createBitmap(width, height)
        draw(Canvas(bitmap), content, width.toFloat(), height.toFloat())
        return bitmap
    }

    private fun textPaint(size: Float, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()

    companion object {
        const val IMAGE_WIDTH = 1080
        const val IMAGE_HEIGHT = 1350
        private const val REFERENCE_WIDTH = 1080f
        private const val FOOTER_COLOR = 0xFF333333.toInt()
        private const val DIVIDER_COLOR = 0xFF9E9E9E.toInt()
    }
}
