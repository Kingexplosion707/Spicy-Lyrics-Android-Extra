@file:Suppress("unused", "UNUSED_PARAMETER")

package android.text

import android.graphics.Canvas
import android.graphics.Paint
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.Paragraph
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle

open class TextPaint(flags: Int = 0) : Paint(flags)

abstract class Layout {
    enum class Alignment { ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER }
    abstract val height: Int
    abstract val width: Int
    abstract val lineCount: Int
    abstract fun draw(canvas: Canvas)
}

/** Wrapped text, laid out by Skia's paragraph layout. */
class StaticLayout private constructor(private val paragraph: Paragraph, override val width: Int) : Layout() {
    override val height: Int get() = kotlin.math.ceil(paragraph.height).toInt()
    override val lineCount: Int get() = paragraph.lineNumber.toInt()
    override fun draw(canvas: Canvas) { paragraph.paint(canvas.skia, 0f, 0f) }

    class Builder private constructor(private val text: CharSequence, private val paint: TextPaint, private val width: Int) {
        private var align = Layout.Alignment.ALIGN_NORMAL
        private var spacingMult = 1f
        private var spacingAdd = 0f
        private var maxLines = Int.MAX_VALUE
        fun setAlignment(a: Layout.Alignment) = apply { align = a }
        fun setLineSpacing(add: Float, mult: Float) = apply { spacingAdd = add; spacingMult = mult }
        fun setIncludePad(b: Boolean) = this
        fun setMaxLines(n: Int) = apply { maxLines = n }
        fun setEllipsize(t: TextUtils.TruncateAt?) = this
        fun setBreakStrategy(s: Int) = this
        fun setHyphenationFrequency(f: Int) = this
        fun build(): StaticLayout {
            val fonts = FontCollection().apply { setDefaultFontManager(FontMgr.default) }
            val style = ParagraphStyle().apply {
                alignment = when (align) { Layout.Alignment.ALIGN_CENTER -> org.jetbrains.skia.paragraph.Alignment.CENTER; Layout.Alignment.ALIGN_OPPOSITE -> org.jetbrains.skia.paragraph.Alignment.END; else -> org.jetbrains.skia.paragraph.Alignment.START }
                if (maxLines != Int.MAX_VALUE) maxLinesCount = maxLines
            }
            val ts = TextStyle().apply {
                color = paint.color
                fontSize = paint.textSize
                height = spacingMult
                paint.typeface?.skia?.let { typeface = it; fontFamilies = arrayOf(it.familyName); fontStyle = it.fontStyle }
            }
            val p = ParagraphBuilder(style, fonts).pushStyle(ts).addText(text.toString()).build()
            p.layout(width.toFloat())
            return StaticLayout(p, width)
        }
        companion object {
            @JvmStatic fun obtain(text: CharSequence, start: Int, end: Int, paint: TextPaint, width: Int) = Builder(text.subSequence(start, end), paint, width)
        }
    }
}
