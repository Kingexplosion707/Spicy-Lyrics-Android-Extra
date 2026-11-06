package com.tx24.spicyplayer.ui.intro

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SpicyButtonStyle
import com.tx24.spicyplayer.ui.components.SpicyModalButton
import com.tx24.spicyplayer.ui.components.SpicySelect
import com.tx24.spicyplayer.ui.components.ToggleRow
import com.tx24.spicyplayer.ui.components.drawBrandMark
import com.tx24.spicyplayer.ui.components.drawBrandRamp
import com.tx24.spicyplayer.ui.components.rememberBrandGlyph
import com.tx24.spicyplayer.ui.components.screenSlide
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.ui.settings.BackgroundType
import com.tx24.spicyplayer.ui.settings.LyricsSize
import com.tx24.spicyplayer.ui.theme.LocalSpicyPalette
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicyPalette
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType

/** The first-run steps, in order; [UsageStats] only when the build reports them and it's unanswered. */
private enum class IntroStep { Welcome, Access, UsageStats, Setup, Gestures }

/**
 * The first-run introduction, over the whole screen: a greeting, the notification-access grant,
 * the usage-stats question, a few settings, and the gestures. Ends with [onDone].
 *
 * @param accessGranted whether notification access is on; the access step moves on by itself
 * once it is, since coming back from the system's settings refreshes it.
 * @param askUsageStats whether to ask about the usage stats; [onUsageStats] gets the answer.
 */
@Composable
fun IntroScreen(
    settings: AppSettings,
    accessGranted: Boolean,
    askUsageStats: Boolean,
    openNotificationAccess: () -> Unit,
    onUsageStats: (keepOn: Boolean) -> Unit,
    onDone: () -> Unit,
) {
    // Fixed at the start, so answering the stats question doesn't pull its step out from under it.
    val steps = remember {
        IntroStep.entries.filter { it != IntroStep.UsageStats || askUsageStats }
    }
    val animate = LocalUiAnimations.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val step = steps[index.coerceIn(steps.indices)]
    // Leaving for the lyrics screen: 0 → 1, then [onDone].
    val exit = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val next: () -> Unit = {
        forward = true
        when {
            index < steps.lastIndex -> index++
            exit.targetValue == 0f -> scope.launch {
                if (animate) exit.animateTo(1f, tween(EXIT_MS, easing = SpicyMotion.Standard)) else exit.snapTo(1f)
                onDone()
            }
        }
    }
    BackHandler(enabled = index > 0) {
        forward = false
        index--
    }
    // Back from the system's settings with access on: on to the next step.
    LaunchedEffect(step, accessGranted) {
        if (step == IntroStep.Access && accessGranted) next()
    }

    val palette = SpicyPalette.Purple
    val brand = palette.brand!!
    val glyph = rememberBrandGlyph()
    CompositionLocalProvider(LocalSpicyPalette provides palette) {
        Box(
            Modifier
                .fillMaxSize()
                // The lyrics screen shows through as the page lifts away.
                .graphicsLayer {
                    alpha = 1f - exit.value
                    val scale = 1f + EXIT_GROW * exit.value
                    scaleX = scale
                    scaleY = scale
                }
                .background(brand.fieldDeep)
                .drawBehind {
                    drawBrandRamp(brand)
                    val width = size.minDimension * 1.1f
                    drawBrandMark(glyph, brand.tint, alpha = 0.35f, width = width, right = -width * 0.32f, top = -width * 0.22f, degrees = -14f)
                },
        ) {
            // Everything a size up from the pop-ups' scale, since this page has the whole screen.
            val density = LocalDensity.current
            val grow = (LocalConfiguration.current.screenWidthDp / 340f).coerceIn(1f, 1.25f)
            CompositionLocalProvider(LocalDensity provides Density(density.density * grow, density.fontScale)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // The content goes first and drifts up; the purple follows it out.
                        alpha = (1f - 2f * exit.value).coerceAtLeast(0f)
                        translationY = -EXIT_RISE.toPx() * exit.value
                    }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = SpicySpacing.S6, vertical = SpicySpacing.S6)
                    .widthIn(max = 480.dp)
                    .align(Alignment.Center),
            ) {
                StepDots(count = steps.size, current = index, modifier = Modifier.align(Alignment.CenterHorizontally))
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { screenSlide(forward, animate) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "introStep",
                ) { shown ->
                    Column(Modifier.fillMaxSize()) {
                        when (shown) {
                            IntroStep.Welcome -> WelcomeStep(next)
                            IntroStep.Access -> AccessStep(accessGranted, openNotificationAccess, next)
                            IntroStep.UsageStats -> UsageStatsStep { keep -> onUsageStats(keep); next() }
                            IntroStep.Setup -> SetupStep(settings, next)
                            IntroStep.Gestures -> GesturesStep(next)
                        }
                    }
                }
            }
            }
        }
    }
}

private const val EXIT_MS = 650
private const val EXIT_GROW = 0.06f
private val EXIT_RISE = 32.dp

/** How far along: one dot per step, the current one long. */
@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = SpicySpacing.S2), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 6.dp, tween(SpicyMotion.BASE_MS, easing = SpicyMotion.Standard), label = "dot")
            val color by animateColorAsState(
                if (i <= current) SpicyColors.TextPrimary else SpicyColors.TextTertiary,
                tween(SpicyMotion.BASE_MS),
                label = "dotColor",
            )
            Box(Modifier.width(width).height(6.dp).clip(CircleShape).background(color))
        }
    }
}

/** A step's frame: the body scrolls in the middle, the buttons stay at the bottom. */
@Composable
private fun ColumnScope.StepFrame(
    centred: Boolean = false,
    buttons: @Composable ColumnScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = SpicySpacing.S6),
        verticalArrangement = if (centred) Arrangement.Center else Arrangement.spacedBy(SpicySpacing.S6, Alignment.CenterVertically),
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
        content = body,
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2), content = buttons)
}

/** A step's heading: a disc with its icon, a big title, and a line under it. */
@Composable
private fun StepHeading(icon: ImageVector, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(SpicyColors.TintBg)
                .border(1.dp, SpicyColors.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) }
        Text(title, style = TitleStyle)
        Text(description, style = SpicyType.Body.copy(color = SpicyColors.TextSecondary, lineHeight = 1.55.em))
    }
}

private val TitleStyle @Composable get() =
    SpicyType.Title.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 1.15.em, letterSpacing = (-0.025f).em, color = SpicyColors.TextPrimary)

@Composable
private fun ColumnScope.WelcomeStep(next: () -> Unit) {
    StepFrame(
        centred = true,
        buttons = { SpicyModalButton("Get started", next, style = SpicyButtonStyle.Primary, fill = true) },
    ) {
        Icon(ImageVector.vectorResource(R.drawable.app_glyph), null, Modifier.size(88.dp), tint = SpicyColors.TextPrimary)
        Spacer(Modifier.height(SpicySpacing.S6))
        Text(
            "Spicy Lyrics Mobile",
            style = TitleStyle.copy(fontSize = 36.sp, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(SpicySpacing.S3))
        Text(
            "Word-synced lyrics for whatever's playing, in whichever music app you use.",
            modifier = Modifier.widthIn(max = 320.dp),
            style = SpicyType.Body.copy(color = SpicyColors.TextSecondary, lineHeight = 1.55.em, textAlign = TextAlign.Center, fontSize = 15.sp),
        )
    }
}

@Composable
private fun ColumnScope.AccessStep(granted: Boolean, openNotificationAccess: () -> Unit, next: () -> Unit) {
    StepFrame(
        buttons = {
            if (granted) {
                SpicyModalButton("Continue", next, style = SpicyButtonStyle.Primary, fill = true)
            } else {
                SpicyModalButton("Open notification access", openNotificationAccess, style = SpicyButtonStyle.Primary, fill = true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val context = LocalContext.current
                    SpicyModalButton("Open app info", {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        }
                    }, style = SpicyButtonStyle.Quiet, fill = true)
                }
            }
        },
    ) {
        StepHeading(
            Icons.Rounded.NotificationsActive,
            "Let it see what's playing",
            "Spicy Lyrics Mobile reads the song playing in your music app, and controls it, through its media notification. " +
                "Android files that under notification access. You can turn it off again in system settings.",
        )
        if (granted) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = SpicyColors.StatusSuccess)
                Text("Access is on", style = SpicyType.Body.copy(fontWeight = FontWeight.Medium, color = SpicyColors.TextPrimary))
            }
        } else {
            Hint("Find Spicy Lyrics Mobile in the list, turn it on, then come back here.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android blocks this switch for apps installed from a downloaded APK.
                Hint(
                    "Switch greyed out, or a \"Restricted setting\" message? Open app info, tap ⋮ in the corner, " +
                        "choose \"Allow restricted settings\", then try again.",
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.UsageStatsStep(onAnswer: (keepOn: Boolean) -> Unit) {
    StepFrame(
        buttons = {
            SpicyModalButton("Keep on", { onAnswer(true) }, style = SpicyButtonStyle.Primary, fill = true)
            SpicyModalButton("Turn off", { onAnswer(false) }, style = SpicyButtonStyle.Quiet, fill = true)
        },
    ) {
        StepHeading(
            Icons.Rounded.Insights,
            "Anonymous usage stats",
            "Spicy Lyrics Mobile sends a small anonymous report a few times a day: the app and Android version, your phone " +
                "model and country, and which features you use. Never what you listen to. It shows what's worth " +
                "working on. You can change this in Settings → Advanced.",
        )
    }
}

@Composable
private fun ColumnScope.SetupStep(settings: AppSettings, next: () -> Unit) {
    StepFrame(buttons = { SpicyModalButton("Continue", next, style = SpicyButtonStyle.Primary, fill = true) }) {
        StepHeading(
            Icons.Rounded.FormatSize,
            "Make it yours",
            "A few settings to start with. Everything else, and these again, lives in Settings.",
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SpicyColors.TintBg).border(1.dp, SpicyColors.Hairline, RoundedCornerShape(16.dp))) {
            SettingRow("Lyrics Size", description = "How big the lyrics are.", icon = Icons.Rounded.FormatSize) {
                SpicySelect(
                    value = LyricsSize.nearest(settings.lyricsSizePortrait).name,
                    options = LyricsSize.entries.map { it.name },
                    labels = LyricsSize.entries.map { it.label },
                    onChange = { settings.setLyricsSize(LyricsSize.valueOf(it)) },
                )
            }
            SettingRow("Background", description = "What fills the screen behind the lyrics.", icon = Icons.Rounded.Wallpaper) {
                SpicySelect(
                    value = settings.backgroundType.name,
                    options = BackgroundType.entries.map { it.name },
                    labels = BackgroundType.entries.map { it.label },
                    onChange = { settings.backgroundType = BackgroundType.valueOf(it) },
                )
            }
            ToggleRow(
                "Romanize Lyrics",
                settings.romanize,
                { settings.romanize = it },
                description = "Shows lyrics in other scripts in Latin letters, where the song has them. The romanize button switches it any time.",
                icon = Icons.Rounded.Translate,
            )
        }
    }
}

@Composable
private fun ColumnScope.GesturesStep(next: () -> Unit) {
    StepFrame(buttons = { SpicyModalButton("Start listening", next, style = SpicyButtonStyle.Primary, fill = true) }) {
        StepHeading(
            Icons.Rounded.TouchApp,
            "A few gestures",
            "Most of the screen is the lyrics, so a lot of it works by touch.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S5)) {
            Gesture(Icons.Rounded.TouchApp, "Tap a line", "Jumps the song to that line.")
            Gesture(Icons.Rounded.SwipeVertical, "Drag the lyrics", "Looks ahead or back through the song.")
            Gesture(Icons.Rounded.Swipe, "Swipe the cover", "Skips to the next or previous song.")
            Gesture(Icons.Rounded.PlayCircle, "Double-tap the cover", "Plays or pauses.")
            Gesture(Icons.Rounded.Visibility, "Tap the screen", "Brings the controls back after they fade.")
        }
    }
}

/** One gesture: its icon in a small tinted square, what to do, and what it does. */
@Composable
private fun Gesture(icon: ImageVector, label: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S4), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(SpicyRadii.Md))
                .background(SpicyColors.TintBg)
                .border(1.dp, SpicyColors.Hairline, RoundedCornerShape(SpicyRadii.Md)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(22.dp), tint = SpicyColors.TextPrimary) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = SpicyType.Headline.copy(color = SpicyColors.TextPrimary))
            Text(description, style = SpicyType.Body.copy(color = SpicyColors.TextSecondary, lineHeight = 1.4.em))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = SpicyType.Caption.copy(color = SpicyColors.TextTertiary, lineHeight = 1.45.em))
}
