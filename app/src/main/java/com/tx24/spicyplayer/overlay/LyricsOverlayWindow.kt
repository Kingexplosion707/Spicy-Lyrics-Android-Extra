package com.tx24.spicyplayer.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.tx24.spicyplayer.ui.ComposeWindowOwner
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The lyrics overlay's window, floating over other apps: adding and removing it, dragging it,
 * locking it in place and letting touches pass through it. Its place is kept as a fraction of
 * the room it can move in, so it stays put across rotations and screen sizes.
 *
 * [content] draws inside; [LyricsOverlayService] keeps the song and lyrics.
 */
internal class LyricsOverlayWindow(
    private val context: Context,
    private val content: @Composable () -> Unit,
) {
    private val windowContext: Context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val manager = context.getSystemService(WindowManager::class.java)
        @Suppress("DEPRECATION")
        context.createDisplayContext(manager.defaultDisplay)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    } else context
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var root: DragFrame? = null
    private var owner: ComposeWindowOwner? = null
    private var params: WindowManager.LayoutParams? = null

    val isShown: Boolean get() = root != null

    /** Shows it; false without the "Display over other apps" permission or when the window can't be added. */
    fun show(): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (root != null) {
            applyBehaviour()
            return true
        }
        val windowOwner = ComposeWindowOwner()
        val frame = DragFrame(windowContext).apply {
            addView(ComposeView(windowContext).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent(content)
            })
        }
        windowOwner.attach(frame)
        val layout = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // The whole screen is its room, the status bar too, so it can sit right at the top.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        return runCatching {
            root = frame
            owner = windowOwner
            params = layout
            place(layout)
            windowManager.addView(frame, layout)
            windowOwner.setActive(true)
            true
        }.getOrElse {
            hide()
            false
        }
    }

    fun hide() {
        val frame = root
        root = null
        params = null
        owner?.destroy()
        owner = null
        if (frame != null) runCatching { windowManager.removeView(frame) }
    }

    /** Re-reads the size, lock and click-through settings and the screen's size. */
    fun applyBehaviour() {
        val frame = root ?: return
        val p = params ?: return
        runCatching {
            p.flags = flags()
            p.alpha = if (clickThrough()) CLICK_THROUGH_ALPHA else 1f
            place(p)
            windowManager.updateViewLayout(frame, p)
        }.onFailure { hide() }
    }

    /** The window's size from the settings, and its place from the saved fraction. */
    private fun place(p: WindowManager.LayoutParams) {
        val (availableW, availableH) = availableSize()
        val density = windowContext.resources.displayMetrics.density
        if (OverlayStyle.from(prefs) == OverlayStyle.StatusBar) {
            placeInStatusBar(p, availableW, density)
            return
        }
        val widthPercent = prefs.getInt(PREF_WIDTH, DEFAULT_WIDTH_PERCENT).coerceIn(45, 100)
        p.width = (availableW * widthPercent / 100f).roundToInt().coerceAtLeast(1)
        p.height = (OverlaySize.from(prefs).heightDp * density).roundToInt().coerceAtMost(availableH)
        val travelX = (availableW - p.width).coerceAtLeast(0)
        val travelY = (availableH - p.height).coerceAtLeast(0)
        p.x = (prefs.getFloat(PREF_X, 0.5f).coerceIn(0f, 1f) * travelX).roundToInt()
        p.y = (prefs.getFloat(PREF_Y, DEFAULT_Y).coerceIn(0f, 1f) * travelY).roundToInt()
        p.alpha = if (clickThrough()) CLICK_THROUGH_ALPHA else 1f
    }

    /**
     * The status bar style: one line in the status bar, from where the clock starts to just
     * before the camera cutout (or across [PREF_WIDTH] of the screen with none in the way).
     */
    private fun placeInStatusBar(p: WindowManager.LayoutParams, availableW: Int, density: Float) {
        val bar = statusBarArea()
        val start = (bar.startPx).coerceAtLeast((STATUS_BAR_START_DP * density).roundToInt())
        val end = bar.cutoutLeftPx?.let { it - (CUTOUT_GAP_DP * density).roundToInt() }
            ?: (availableW * prefs.getInt(PREF_WIDTH, DEFAULT_WIDTH_PERCENT).coerceIn(45, 100) / 100f).roundToInt()
        p.x = start
        p.y = 0
        p.width = (end - start).coerceAtLeast((48 * density).roundToInt())
        p.height = bar.heightPx.coerceAtLeast((24 * density).roundToInt())
        p.alpha = if (clickThrough()) CLICK_THROUGH_ALPHA else 1f
    }

    private class StatusBarArea(val heightPx: Int, val startPx: Int, val cutoutLeftPx: Int?)

    /** The status bar's height, its left inset, and where a top cutout in its middle starts. */
    @Suppress("DEPRECATION")
    private fun statusBarArea(): StatusBarArea {
        val density = windowContext.resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets
            val bar = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
            val width = metrics.bounds.width()
            val cutout = insets.displayCutout?.boundingRectTop?.takeIf { !it.isEmpty && it.left > width / 4 }
            return StatusBarArea(bar.top, bar.left, cutout?.left)
        }
        val id = windowContext.resources.getIdentifier("status_bar_height", "dimen", "android")
        val height = if (id != 0) windowContext.resources.getDimensionPixelSize(id) else (24 * density).roundToInt()
        return StatusBarArea(height, 0, null)
    }

    private fun savePlace(p: WindowManager.LayoutParams) {
        val (availableW, availableH) = availableSize()
        val travelX = (availableW - p.width).coerceAtLeast(0)
        val travelY = (availableH - p.height).coerceAtLeast(0)
        prefs.edit()
            .putFloat(PREF_X, if (travelX > 0) p.x.toFloat() / travelX else 0.5f)
            .putFloat(PREF_Y, if (travelY > 0) p.y.toFloat() / travelY else DEFAULT_Y)
            .apply()
    }

    /** The whole screen, where the window's coordinates live. */
    @Suppress("DEPRECATION")
    private fun availableSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
        }
        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        return size.x.coerceAtLeast(1) to size.y.coerceAtLeast(1)
    }

    private fun locked() = prefs.getBoolean(PREF_LOCKED, false)
    private fun clickThrough() = prefs.getBoolean(PREF_CLICK_THROUGH, false)

    private fun flags(): Int {
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return if (clickThrough()) {
            base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else base
    }

    /** Takes every touch for itself: a drag moves the window, unless it's locked. */
    @SuppressLint("ViewConstructor")
    private inner class DragFrame(context: Context) : FrameLayout(context) {
        private var startX = 0
        private var startY = 0
        private var downX = 0f
        private var downY = 0f
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val p = params ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    startX = p.x
                    startY = p.y
                    downX = event.rawX
                    downY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (locked() || OverlayStyle.from(prefs) == OverlayStyle.StatusBar) return true
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && abs(dx) <= touchSlop && abs(dy) <= touchSlop) return true
                    dragging = true
                    val (availableW, availableH) = availableSize()
                    val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
                    p.x = (startX + dx.toInt() * if (rtl) -1 else 1).coerceIn(0, (availableW - p.width).coerceAtLeast(0))
                    p.y = (startY + dy.toInt()).coerceIn(0, (availableH - p.height).coerceAtLeast(0))
                    runCatching { windowManager.updateViewLayout(this, p) }.onFailure { hide() }
                }
                MotionEvent.ACTION_UP -> if (dragging) savePlace(p) else performClick()
                MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }

        override fun performClick(): Boolean = super.performClick()
    }

    companion object {
        const val PREF_X = "overlayX"
        const val PREF_Y = "overlayY"
        const val PREF_WIDTH = "overlayWidth"
        const val PREF_LOCKED = "overlayLocked"
        const val PREF_CLICK_THROUGH = "overlayClickThrough"
        const val DEFAULT_WIDTH_PERCENT = 94
        /** Near the top by default, under the status bar. */
        private const val DEFAULT_Y = 0.06f
        /** The status bar style starts where a status bar's clock does, at least this far in. */
        private const val STATUS_BAR_START_DP = 20
        /** ...and stops this short of a camera cutout. */
        private const val CUTOUT_GAP_DP = 8
        /**
         * Android 12+ blocks touches that pass through another app's window more opaque than
         * 0.8, so a click-through overlay is drawn a little under it (exactly 0.8 can round over).
         */
        private const val CLICK_THROUGH_ALPHA = 0.75f
    }
}

/** What the overlay looks like: the lyrics as on the lyrics screen, or one line in the status bar. */
internal enum class OverlayStyle(val label: String) {
    Full("Lyrics"),
    StatusBar("Status bar");

    companion object {
        const val PREF = "overlayStyle"
        fun from(prefs: android.content.SharedPreferences): OverlayStyle =
            runCatching { valueOf(prefs.getString(PREF, Full.name)!!) }.getOrDefault(Full)
    }
}

/** How big the overlay is: its height, and its lyrics' size against the lyrics screen's. */
internal enum class OverlaySize(val label: String, val heightDp: Int, val fontScale: Float) {
    Small("Small", 130, 0.62f),
    Medium("Medium", 180, 0.78f),
    Large("Large", 240, 0.95f);

    companion object {
        const val PREF = "overlaySize"
        fun from(prefs: android.content.SharedPreferences): OverlaySize =
            runCatching { valueOf(prefs.getString(PREF, Medium.name)!!) }.getOrDefault(Medium)
    }
}
