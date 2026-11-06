package com.tx24.spicyplayer.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.lyrics.spicy.parser.RtlDetector

/**
 * The widget's lyrics, drawn as one picture so they can use the lyrics font (a widget's text
 * views only get system fonts). The line being sung sits in the middle, glowing, with the lines
 * around it above and below, faded out at the edges like the lyrics view. Under each line: its
 * romanization and translation, then its background vocals, smaller. A break is three dots, each
 * lit as it finishes. In a song with a duet, each line is inset on the side it leans away from.
 */
internal class WidgetLyricsPainter(context: Context) {
    private val bold: Typeface = ResourcesCompat.getFont(context, R.font.lyrics_bold) ?: Typeface.DEFAULT_BOLD
    private val semibold: Typeface = ResourcesCompat.getFont(context, R.font.lyrics_semibold) ?: Typeface.DEFAULT_BOLD

    /** The lyrics area's look, beyond [WidgetSettings]. */
    data class Style(
        /** The song has a duet: lines lean away from the side their singer isn't on. */
        val duet: Boolean,
        /** Duet Line Padding: the wide (15%) duet inset rather than the narrow one. */
        val wideDuet: Boolean,
    )

    /**
     * The lyrics for an area [widthPx] by [heightPx], plus [BLEED_DP] each side for the glow to
     * spill into (the view reaches past the widget's padding by as much).
     */
    fun paint(lines: WidgetLines, settings: WidgetSettings, style: Style, areaWidthPx: Int, heightPx: Int, density: Float): Bitmap {
        val bleed = (BLEED_DP * density).toInt()
        val bitmap = Bitmap.createBitmap(areaWidthPx.coerceAtLeast(1) + bleed * 2, heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(bleed.toFloat(), 0f)
        val widthPx = areaWidthPx.coerceAtLeast(1)
        val gap = LINE_GAP_DP * density
        val before = lines.before.takeLast(settings.around)
        val after = lines.after.take(settings.around)

        // Everything shrinks together, up to [MIN_SCALE], for one row of the current line and of
        // each line around it to fit. It goes by the widget's size and settings alone, so the text
        // keeps its size from one line to the next. Laid out that much wider, the lines still
        // reach both edges once scaled, and keep their alignment.
        val scale = fitScale(settings, heightPx, density)
        val layoutWidth = (widthPx / scale).toInt().coerceAtLeast(1)
        val visibleHeight = heightPx / scale

        val active = (listOf(lines.current) + lines.concurrent).map {
            block(it, Role.CURRENT, settings, style, layoutWidth, density, lines.dots)
        }
        val current = Block(active.sumOf { it.height.toDouble() }.toFloat() + gap * (active.size - 1)) { target ->
            var y = 0f
            for (block in active) {
                target.save(); target.translate(0f, y); block.draw(target); target.restore()
                y += block.height + gap
            }
        }
        val above = before.reversed().mapIndexed { i, lead -> block(lead, if (i == 0) Role.NEAR else Role.FAR, settings, style, layoutWidth, density) }
        val below = after.mapIndexed { i, lead -> block(lead, if (i == 0) Role.NEAR else Role.FAR, settings, style, layoutWidth, density) }

        canvas.scale(scale, scale)
        // Lines that don't fit give way to the current one, which never clips: too tall on its
        // own, it shrinks further to fit.
        val squeeze = (visibleHeight / current.height).coerceAtMost(1f)
        val top = (visibleHeight - current.height * squeeze) / 2f
        canvas.save()
        canvas.translate(0f, top)
        canvas.scale(squeeze, squeeze)
        current.draw(canvas)
        canvas.restore()
        if (squeeze == 1f) {
            var y = top
            for (block in above) {
                y -= gap + block.height
                if (y + block.height <= 0f) break
                canvas.save(); canvas.translate(0f, y); block.draw(canvas); canvas.restore()
            }
            y = top + current.height
            for (block in below) {
                y += gap
                if (y >= visibleHeight) break
                canvas.save(); canvas.translate(0f, y); block.draw(canvas); canvas.restore()
                y += block.height
            }
            fadeEdges(canvas, bleed / scale, layoutWidth, visibleHeight, (visibleHeight * EDGE_FADE).coerceAtMost(top))
        }
        return bitmap
    }

    /** How much the lyrics shrink in an area [heightPx] tall: [MIN_SCALE] to 1, by [settings] alone. */
    private fun fitScale(settings: WidgetSettings, heightPx: Int, density: Float): Float {
        val gap = LINE_GAP_DP * density
        val wanted = rowPx(settings.textSize.currentSp * density) +
            settings.around * 2 * (rowPx(settings.textSize.aroundSp * density) + gap)
        return (heightPx / wanted).coerceIn(MIN_SCALE, 1f)
    }

    /** One row of the lyrics font at [sizePx], as [layout] spaces it. */
    private fun rowPx(sizePx: Float): Float {
        val metrics = TextPaint().apply { typeface = bold; textSize = sizePx }.fontMetrics
        return (metrics.descent - metrics.ascent) * LINE_SPACING
    }

    private enum class Role(val alpha: Float) { CURRENT(1f), NEAR(0.45f), FAR(0.28f) }

    /** One lead line and what goes under it, laid out at the top of its own space. */
    private class Block(val height: Float, val draw: (Canvas) -> Unit)

    private fun block(lead: WidgetLines.Lead, role: Role, settings: WidgetSettings, style: Style, widthPx: Int, density: Float, dots: Int = 0): Block {
        val current = role == Role.CURRENT
        val sizePx = (if (current) settings.textSize.currentSp else settings.textSize.aroundSp) * density
        val rtl = RtlDetector.isRtl(lead.text)
        val right = !settings.centered && if (style.duet) rtl != lead.oppositeAligned else rtl
        // The lyrics view insets every line 5% each side and a duet line its far side by 15%; the
        // widget's own padding stands for the 5%, so a duet line gets the rest.
        val inset = if (style.duet && style.wideDuet && !settings.centered) widthPx * (WIDE_DUET_INSET - BASE_INSET) else 0f
        val left = if (style.duet && right) inset else 0f
        val width = (widthPx - inset).toInt().coerceAtLeast(1)
        val align = when {
            settings.centered -> Layout.Alignment.ALIGN_CENTER
            right != rtl -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        if (lead.interlude) return dotsBlock(sizePx, dots, settings.centered, right, widthPx)

        val rows = mutableListOf<Pair<StaticLayout, Float>>()
        val main = layout(lead.text.ifEmpty { lead.romanized.orEmpty() }, bold, sizePx, role.alpha, width, align, if (current) MAX_CURRENT_LINES else MAX_AROUND_LINES, glow = current)
        rows += main to 0f
        val noteSize = (settings.textSize.noteSp + if (current) 1f else 0f) * density
        val noteAlpha = if (current) 0.7f else role.alpha * 0.75f
        lead.romanized?.takeIf { settings.romanization && lead.text.isNotEmpty() }?.let {
            rows += layout(it, semibold, noteSize, noteAlpha, width, align, 2) to NOTE_GAP_DP * density
        }
        lead.translation?.takeIf { settings.translation }?.let {
            rows += layout(it, semibold, noteSize, noteAlpha, width, align, 2) to NOTE_GAP_DP * density
        }
        lead.background?.lineSequence()?.forEach {
            rows += layout(it, bold, sizePx * BACKGROUND_SCALE, role.alpha * BACKGROUND_ALPHA, width, align, 2) to NOTE_GAP_DP * density
        }
        val height = rows.sumOf { (layout, gapBefore) -> (layout.height + gapBefore).toDouble() }.toFloat()
        return Block(height) { canvas ->
            var y = 0f
            for ((layout, gapBefore) in rows) {
                y += gapBefore
                canvas.save(); canvas.translate(left, y); layout.draw(canvas); canvas.restore()
                y += layout.height
            }
        }
    }

    private fun layout(text: String, typeface: Typeface, sizePx: Float, alpha: Float, width: Int, align: Layout.Alignment, maxLines: Int, glow: Boolean = false): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = sizePx
            color = Color.argb((alpha * 255).toInt(), 255, 255, 255)
            if (glow) setShadowLayer(sizePx * GLOW_RADIUS, 0f, 0f, Color.argb(GLOW_ALPHA, 255, 255, 255))
        }
        // The glow spills past the text, so the room it draws in leaves space for it.
        val unbroken = widgetUnbreakableText(text)
        return StaticLayout.Builder.obtain(unbroken, 0, unbroken.length, paint, width)
            .setAlignment(align)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setLineSpacing(0f, LINE_SPACING)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }

    /** A break: three dots, lit up to [lit], the lit ones a little larger and glowing. */
    private fun dotsBlock(sizePx: Float, lit: Int, centered: Boolean, right: Boolean, widthPx: Int): Block {
        val radius = sizePx * DOT_SIZE / 2f
        val step = radius * 2f * (1f + DOT_GAP)
        val rowWidth = step * 2f + radius * 2f
        val startX = when {
            centered -> (widthPx - rowWidth) / 2f
            right -> widthPx - rowWidth
            else -> 0f
        }
        val height = sizePx * LINE_SPACING
        return Block(height) { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until 3) {
                val on = i < lit
                paint.color = Color.argb(if (on) 255 else (255 * DOT_UNLIT_ALPHA).toInt(), 255, 255, 255)
                if (on) paint.setShadowLayer(radius * 1.5f, 0f, 0f, Color.argb(GLOW_ALPHA, 255, 255, 255)) else paint.clearShadowLayer()
                canvas.drawCircle(startX + radius + step * i, height / 2f, radius * if (on) DOT_LIT_SCALE else 1f, paint)
            }
        }
    }

    /** Fades the picture out over [fadePx] at its top and bottom, as the lyrics view's edge mask does. */
    private fun fadeEdges(canvas: Canvas, bleed: Float, width: Int, height: Float, fadePx: Float) {
        if (fadePx <= 1f) return
        val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        val top = fadePx / height
        mask.shader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, top, 1f - top, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(-bleed, 0f, width + bleed, height, mask)
    }

    companion object {
        /** How far the picture reaches past the lyrics area on each side, for the glow. */
        const val BLEED_DP = 8
        private const val LINE_GAP_DP = 6f
        private const val NOTE_GAP_DP = 2f
        private const val LINE_SPACING = 1.08f
        /** The smallest the lyrics shrink to fit the lines around the current one. */
        private const val MIN_SCALE = 0.65f
        private const val MAX_CURRENT_LINES = 3
        private const val MAX_AROUND_LINES = 2
        /** Background vocals, against their lead line: the lyrics view's size, dimmer. */
        private const val BACKGROUND_SCALE = 0.75f
        private const val BACKGROUND_ALPHA = 0.6f
        private const val BASE_INSET = 0.05f
        private const val WIDE_DUET_INSET = 0.15f
        private const val GLOW_RADIUS = 0.35f
        private const val GLOW_ALPHA = 90
        private const val EDGE_FADE = 0.14f
        private const val DOT_SIZE = 0.32f
        private const val DOT_GAP = 0.7f
        private const val DOT_LIT_SCALE = 1.15f
        private const val DOT_UNLIT_ALPHA = 0.3f
    }
}
