@file:Suppress("unused", "UNUSED_PARAMETER")

package android.graphics

import androidx.compose.ui.graphics.skiaShader
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode

/** Android shaders build their Skia shader when drawn. */
abstract class Shader {
    enum class TileMode(val skia: FilterTileMode, val compose: androidx.compose.ui.graphics.TileMode) { CLAMP(FilterTileMode.CLAMP, androidx.compose.ui.graphics.TileMode.Clamp), REPEAT(FilterTileMode.REPEAT, androidx.compose.ui.graphics.TileMode.Repeated), MIRROR(FilterTileMode.MIRROR, androidx.compose.ui.graphics.TileMode.Mirror), DECAL(FilterTileMode.DECAL, androidx.compose.ui.graphics.TileMode.Decal) }
    private var local: Matrix? = null
    internal val localMatrix: Matrix? get() = local
    fun setLocalMatrix(m: Matrix?) { local = m?.let { Matrix(it) } }
    fun getLocalMatrix(out: Matrix): Boolean { local?.let { out.set(it) }; return local != null }
    abstract fun skia(): org.jetbrains.skia.Shader
}

class BitmapShader(val bitmap: Bitmap, val tileX: TileMode, val tileY: TileMode) : Shader() {
    var filterMode: Int = FILTER_MODE_DEFAULT
    override fun skia(): org.jetbrains.skia.Shader =
        bitmap.image().makeShader(tileX.skia, tileY.skia, if (filterMode == FILTER_MODE_NEAREST) SamplingMode.DEFAULT else SamplingMode.LINEAR, localMatrix?.skia)
    companion object { const val FILTER_MODE_DEFAULT = 0; const val FILTER_MODE_NEAREST = 1; const val FILTER_MODE_LINEAR = 2 }
}

class LinearGradient(private val x0: Float, private val y0: Float, private val x1: Float, private val y1: Float, private val colors: IntArray, private val positions: FloatArray?, private val tile: TileMode) : Shader() {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, tile: TileMode) : this(x0, y0, x1, y1, intArrayOf(c0, c1), null, tile)
    override fun skia(): org.jetbrains.skia.Shader = androidx.compose.ui.graphics.LinearGradientShader(
        androidx.compose.ui.geometry.Offset(x0, y0), androidx.compose.ui.geometry.Offset(x1, y1), colors.map { androidx.compose.ui.graphics.Color(it) }, positions?.toList(), tile.compose).skiaShader
}
class RadialGradient(private val x: Float, private val y: Float, private val r: Float, private val colors: IntArray, private val positions: FloatArray?, private val tile: TileMode) : Shader() {
    constructor(x: Float, y: Float, r: Float, c0: Int, c1: Int, tile: TileMode) : this(x, y, r, intArrayOf(c0, c1), null, tile)
    override fun skia(): org.jetbrains.skia.Shader = androidx.compose.ui.graphics.RadialGradientShader(
        androidx.compose.ui.geometry.Offset(x, y), r, colors.map { androidx.compose.ui.graphics.Color(it) }, positions?.toList(), tile.compose).skiaShader
}
class SweepGradient(private val x: Float, private val y: Float, private val colors: IntArray, private val positions: FloatArray?) : Shader() {
    override fun skia(): org.jetbrains.skia.Shader = androidx.compose.ui.graphics.SweepGradientShader(
        androidx.compose.ui.geometry.Offset(x, y), colors.map { androidx.compose.ui.graphics.Color(it) }, positions?.toList()).skiaShader
}
class ComposeShader(private val a: Shader, private val b: Shader, private val mode: BlendMode) : Shader() {
    constructor(a: Shader, b: Shader, mode: PorterDuff.Mode) : this(a, b, BlendMode.valueOf(mode.name.replace("ADD", "PLUS")))
    override fun skia() = org.jetbrains.skia.Shader.makeBlend(mode.skia, a.skia(), b.skia())
}

/** AGSL is Skia's SkSL: the same source runs as a Skia runtime effect. */
class RuntimeShader(source: String) : Shader() {
    private val effect = RuntimeEffect.makeForShader(source)
    private val builder = RuntimeShaderBuilder(effect)
    private val inputs = mutableMapOf<String, Shader>()
    fun setFloatUniform(name: String, v: Float) = builder.uniform(name, v)
    fun setFloatUniform(name: String, a: Float, b: Float) = builder.uniform(name, a, b)
    fun setFloatUniform(name: String, a: Float, b: Float, c: Float) = builder.uniform(name, a, b, c)
    fun setFloatUniform(name: String, a: Float, b: Float, c: Float, d: Float) = builder.uniform(name, a, b, c, d)
    fun setFloatUniform(name: String, v: FloatArray) = builder.uniform(name, v)
    fun setIntUniform(name: String, v: Int) = builder.uniform(name, v)
    fun setIntUniform(name: String, a: Int, b: Int) = builder.uniform(name, a, b)
    fun setColorUniform(name: String, c: Int) = builder.uniform(name, Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f, Color.alpha(c) / 255f)
    fun setInputShader(name: String, s: Shader) { inputs[name] = s }
    fun setInputBuffer(name: String, s: BitmapShader) { inputs[name] = s }
    override fun skia(): org.jetbrains.skia.Shader {
        inputs.forEach { (n, s) -> builder.child(n, s.skia()) }
        return builder.makeShader(localMatrix?.skia)
    }
}

/** Android render effects as Skia image filters. */
class RenderEffect private constructor(val skia: ImageFilter) {
    companion object {
        @JvmStatic fun createBlurEffect(rx: Float, ry: Float, tile: Shader.TileMode): RenderEffect = RenderEffect(ImageFilter.makeBlur(rx, ry, tile.skia))
        @JvmStatic fun createBlurEffect(rx: Float, ry: Float, input: RenderEffect, tile: Shader.TileMode): RenderEffect = RenderEffect(ImageFilter.makeBlur(rx, ry, tile.skia, input.skia))
        @JvmStatic fun createColorFilterEffect(f: ColorFilter): RenderEffect = RenderEffect(ImageFilter.makeColorFilter(f.skia!!, null, null))
        @JvmStatic fun createColorFilterEffect(f: ColorFilter, input: RenderEffect): RenderEffect = RenderEffect(ImageFilter.makeColorFilter(f.skia!!, input.skia, null))
        @JvmStatic fun createOffsetEffect(dx: Float, dy: Float): RenderEffect = RenderEffect(ImageFilter.makeOffset(dx, dy, null, null))
        @JvmStatic fun createOffsetEffect(dx: Float, dy: Float, input: RenderEffect): RenderEffect = RenderEffect(ImageFilter.makeOffset(dx, dy, input.skia, null))
        @JvmStatic fun createShaderEffect(s: Shader): RenderEffect = RenderEffect(ImageFilter.makeShader(s.skia(), false, null))
        @JvmStatic fun createChainEffect(outer: RenderEffect, inner: RenderEffect): RenderEffect = RenderEffect(ImageFilter.makeCompose(outer.skia, inner.skia))
        @JvmStatic fun createBlendModeEffect(dst: RenderEffect, src: RenderEffect, mode: BlendMode): RenderEffect = RenderEffect(ImageFilter.makeBlend(mode.skia, dst.skia, src.skia, null))
        @JvmStatic fun createRuntimeShaderEffect(s: RuntimeShader, input: String): RenderEffect = RenderEffect(ImageFilter.makeShader(s.skia(), false, null))
    }
}
