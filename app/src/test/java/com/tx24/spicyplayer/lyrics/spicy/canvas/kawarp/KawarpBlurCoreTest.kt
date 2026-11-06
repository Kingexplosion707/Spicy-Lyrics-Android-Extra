package com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class KawarpBlurCoreTest {
    @Test
    fun `the fast path blurs like the reference one`() {
        val random = Random(7)
        for ((w, h) in listOf(64 to 64, 48 to 64, 200 to 120)) {
            val src = FloatArray(w * h * 4) { if (it % 4 == 3) 1f else random.nextFloat() }
            for (passes in listOf(1, 8, 20)) {
                val reference = KawarpBlurCore.process(src, w, h, passes, floatArrayOf(0f, 0f, 0f), 0f)
                val fast = KawarpBlurCore.processFast(src, w, h, passes)
                var worst = 0f
                for (i in reference.indices) worst = maxOf(worst, kotlin.math.abs(reference[i] - fast[i]))
                assertEquals("${w}x$h, $passes passes", 0f, worst, 1e-4f)
            }
        }
    }
}
