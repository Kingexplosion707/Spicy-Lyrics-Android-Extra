package com.tx24.spicyplayer.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.playback.SleepTimerService
import com.tx24.spicyplayer.ui.components.SpicyButtonStyle
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.SpicyModalActions
import com.tx24.spicyplayer.ui.components.SpicyModalButton
import com.tx24.spicyplayer.ui.components.SpicyModalGap
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/** The sleep timer's choices: when to pause the music, or turning a running timer off. */
@Composable
internal fun SleepTimerModal(visible: Boolean, backdrop: HazeState?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    SpicyModal(visible = visible, onDismissRequest = onDismiss, backdrop = backdrop, title = "Sleep timer") {
        val timer = SleepTimerService.state.value
        // The minutes left tick down while it's open.
        var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
        LaunchedEffect(timer) {
            while (timer != null && !timer.endOfSong) {
                now = android.os.SystemClock.elapsedRealtime()
                delay(1_000L)
            }
        }
        Text(
            when {
                timer == null -> "Pause the music after a while. It fades out first."
                timer.endOfSong -> "The music pauses when this song ends."
                else -> "The music pauses in ${minutesText(timer.minutesLeft(now))}."
            },
            style = SpicyType.Body.copy(color = SpicyColors.TextSecondary),
        )
        SpicyModalGap()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CHOICES.forEach { minutes ->
                Choice(if (minutes == 0) "End of this song" else minutesText(minutes)) {
                    SleepTimerService.start(context, minutes)
                    onDismiss()
                }
            }
        }
        if (timer != null) {
            SpicyModalGap()
            SpicyModalActions {
                SpicyModalButton("Turn off", {
                    SleepTimerService.cancel(context)
                    onDismiss()
                }, style = SpicyButtonStyle.Primary)
            }
        }
    }
}

@Composable
private fun Choice(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    Text(
        label,
        style = SpicyType.Body.copy(color = SpicyColors.TextPrimary, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SpicyColors.TintBg)
            .border(1.dp, SpicyColors.Hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SpicySpacing.S3, vertical = 10.dp),
    )
}

private fun minutesText(minutes: Int): String = when {
    minutes >= 60 && minutes % 60 == 0 -> if (minutes == 60) "1 hour" else "${minutes / 60} hours"
    minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"
    minutes == 1 -> "1 minute"
    else -> "$minutes minutes"
}

/** Minutes to choose from; 0 is the end of the song. */
private val CHOICES = listOf(15, 30, 45, 60, 90, 0)
