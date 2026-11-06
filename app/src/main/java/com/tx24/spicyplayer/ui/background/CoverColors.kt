package com.tx24.spicyplayer.ui.background

import kotlin.math.pow

/**
 * The Color background's three shades of a cover, as ARGB ints: its main colour darkened just
 * enough for white text to read on it at three strengths (contrast against white of at least
 * [MIN_CONTRAST], [HIGH_CONTRAST] and [HIGHER_CONTRAST]). A cover already dark enough keeps its
 * colour. These stand in for the colours Spotify extracts from its own covers, which calibrate them:
 * its three shades of a grey cover come out at contrasts of about 2.9, 7.7 and 12.3.
 */
data class CoverColors(val minContrast: Int, val highContrast: Int, val higherContrast: Int) {
    companion object {
        const val MIN_CONTRAST = 2.8
        const val HIGH_CONTRAST = 7.5
        const val HIGHER_CONTRAST = 12.0

        /** From a small copy of the cover's pixels (ARGB). */
        fun of(pixels: IntArray): CoverColors {
            val base = mainColor(pixels)
            return CoverColors(
                darkenTo(base, MIN_CONTRAST),
                darkenTo(base, HIGH_CONTRAST),
                darkenTo(base, HIGHER_CONTRAST),
            )
        }

        /**
         * The cover's main colour: pixels are pooled by colour (4 bits a channel) and the pool
         * with the most pixels wins, weighted towards colourful pools and away from the near-black
         * and near-white of borders and text. The winner is its pixels' average.
         */
        internal fun mainColor(pixels: IntArray): Int {
            val count = IntArray(4096)
            val sumR = LongArray(4096)
            val sumG = LongArray(4096)
            val sumB = LongArray(4096)
            for (p in pixels) {
                if ((p ushr 24) < 128) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                count[key]++
                sumR[key] += r.toLong(); sumG[key] += g.toLong(); sumB[key] += b.toLong()
            }
            var best = -1
            var bestScore = -1.0
            for (key in 0 until 4096) {
                val n = count[key]
                if (n == 0) continue
                val r = (sumR[key] / n).toInt(); val g = (sumG[key] / n).toInt(); val b = (sumB[key] / n).toInt()
                val max = maxOf(r, g, b); val min = minOf(r, g, b)
                val chroma = (max - min) / 255.0
                val lightness = (max + min) / 510.0
                val extreme = if (lightness < 0.08 || lightness > 0.92) 0.4 else 1.0
                val score = n * (0.35 + chroma) * extreme
                if (score > bestScore) { bestScore = score; best = key }
            }
            if (best < 0) return BLACK
            val n = count[best]
            return argb((sumR[best] / n).toInt(), (sumG[best] / n).toInt(), (sumB[best] / n).toInt())
        }

        /** [color] with its lightness lowered until white on it reaches [contrast]. */
        internal fun darkenTo(color: Int, contrast: Double): Int {
            if (contrastWithWhite(color) >= contrast) return color
            val (h, s, l) = toHsl(color)
            var lo = 0.0
            var hi = l
            // Contrast rises as lightness falls, so halve the gap to the lightest shade that reaches it.
            repeat(20) {
                val mid = (lo + hi) / 2
                if (contrastWithWhite(fromHsl(h, s, mid)) >= contrast) lo = mid else hi = mid
            }
            return fromHsl(h, s, lo)
        }

        internal fun contrastWithWhite(color: Int): Double = 1.05 / (luminance(color) + 0.05)

        private fun luminance(color: Int): Double {
            fun channel(c: Int): Double {
                val v = c / 255.0
                return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel((color shr 16) and 0xFF) +
                0.7152 * channel((color shr 8) and 0xFF) +
                0.0722 * channel(color and 0xFF)
        }

        private fun toHsl(color: Int): Triple<Double, Double, Double> {
            val r = ((color shr 16) and 0xFF) / 255.0
            val g = ((color shr 8) and 0xFF) / 255.0
            val b = (color and 0xFF) / 255.0
            val max = maxOf(r, g, b); val min = minOf(r, g, b)
            val l = (max + min) / 2
            if (max == min) return Triple(0.0, 0.0, l)
            val d = max - min
            val s = if (l > 0.5) d / (2 - max - min) else d / (max + min)
            val h = when (max) {
                r -> (g - b) / d + (if (g < b) 6 else 0)
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            } / 6
            return Triple(h, s, l)
        }

        private fun fromHsl(h: Double, s: Double, l: Double): Int {
            if (s == 0.0) {
                val v = (l * 255).toInt().coerceIn(0, 255)
                return argb(v, v, v)
            }
            val q = if (l < 0.5) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            fun hue(t0: Double): Double {
                var t = t0
                if (t < 0) t += 1.0
                if (t > 1) t -= 1.0
                return when {
                    t < 1.0 / 6 -> p + (q - p) * 6 * t
                    t < 1.0 / 2 -> q
                    t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                    else -> p
                }
            }
            return argb(
                (hue(h + 1.0 / 3) * 255).toInt().coerceIn(0, 255),
                (hue(h) * 255).toInt().coerceIn(0, 255),
                (hue(h - 1.0 / 3) * 255).toInt().coerceIn(0, 255),
            )
        }

        private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        private const val BLACK = 0xFF000000.toInt()
    }
}
