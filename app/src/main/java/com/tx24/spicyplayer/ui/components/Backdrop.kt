package com.tx24.spicyplayer.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * What glass surfaces blur. A screen marks its backdrop (background, lyrics) with
 * `Modifier.hazeSource(state)` and provides the state here; glass without one falls back to its
 * translucent fill.
 */
val LocalBackdrop = staticCompositionLocalOf<HazeState?> { null }
