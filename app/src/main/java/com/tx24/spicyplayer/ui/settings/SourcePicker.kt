package com.tx24.spicyplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType

/**
 * The lyrics sources this song could use: the automatic pick first, then every source that's
 * on, in the user's order, with what each had. One that wasn't asked yet is asked when chosen.
 */
@Composable
fun SourceList(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, onChosen: () -> Unit, modifier: Modifier = Modifier) {
    val choices = remember(state.providerAttempts, state.sourceOverride, state.lyrics) { viewModel.sourceChoices() }
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "automatic") {
            SourceRow(
                name = "Automatic",
                status = "The best lyrics your sources have, in your order",
                selected = state.sourceOverride == null,
                enabled = true,
            ) { viewModel.useSource(null); onChosen() }
        }
        items(choices, key = { it.id }) { choice ->
            SourceRow(
                name = choice.name,
                status = if (choice.shown && state.sourceOverride == null) "${choice.status} · showing now" else choice.status,
                selected = state.sourceOverride == choice.id,
                enabled = choice.available,
                action = if (choice.canModelSync) {
                    {
                        SpicyButton(
                            text = if (state.modelSyncing) "Timing every word…" else "Word-sync with lrc.red's model",
                            onClick = viewModel::requestModelSync,
                            enabled = !state.modelSyncing,
                        )
                    }
                } else null,
            ) { viewModel.useSource(choice.id); onChosen() }
        }
    }
}

@Composable
private fun SourceRow(
    name: String,
    status: String,
    selected: Boolean,
    enabled: Boolean,
    /** A button of its own under the status, such as asking for a model sync. */
    action: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SpicyRadii.Md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) SpicyColors.TintBg else Color.Transparent)
            .border(1.dp, if (selected) SpicyColors.HairlineStrong else Color.Transparent, shape)
            .clickable(enabled = enabled && !selected, role = Role.RadioButton, onClick = onClick)
            .alpha(if (enabled || selected) 1f else 0.5f)
            .padding(horizontal = SpicySpacing.S3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Box(Modifier.size(8.dp).background(if (selected) SpicyColors.StatusSuccess else Color.Transparent, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = SpicyType.Body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            )
            Text(status, maxLines = 2, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary))
            if (action != null) {
                Box(Modifier.padding(top = SpicySpacing.S2)) { action() }
            }
        }
    }
}
