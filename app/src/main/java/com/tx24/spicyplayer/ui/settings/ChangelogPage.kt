package com.tx24.spicyplayer.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.ui.components.ReleaseNotesView
import com.tx24.spicyplayer.ui.components.SettingsSkeleton
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.outlinedCard
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import com.tx24.spicyplayer.update.AppRelease
import com.tx24.spicyplayer.update.ChangelogState
import com.tx24.spicyplayer.update.ReleaseNotes
import com.tx24.spicyplayer.update.Releases
import com.tx24.spicyplayer.update.UpdateViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Every release's notes, newest first, from the repository's GitHub Releases: one card each,
 * closed to a line (version, date, how many changes) until tapped. The newest starts open.
 */
@Composable
internal fun ChangelogContent(updater: UpdateViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { updater.loadChangelog() }
    val state by updater.changelog.collectAsState()
    Spacer(Modifier.height(SpicySpacing.S4))
    when (val now = state) {
        ChangelogState.Idle, ChangelogState.Loading -> SettingsSkeleton(rows = 6, cards = true)
        ChangelogState.Failed -> Column(
            Modifier.fillMaxWidth().outlinedCard().padding(SpicySpacing.S4),
            verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
        ) {
            Text("Couldn't reach GitHub. Try again later.", style = SpicyType.Body.copy(color = SpicyColors.TextSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                SpicyButton("Try again", updater::loadChangelog)
                SpicyButton("Open on GitHub", { openUrl(context, RELEASES_PAGE) }, external = true)
            }
        }
        is ChangelogState.Loaded -> Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
            now.releases.forEachIndexed { index, release ->
                ReleaseCard(release, latest = index == 0, startOpen = index == 0)
            }
            SpicyButton("All releases on GitHub", { openUrl(context, RELEASES_PAGE) }, Modifier.align(Alignment.CenterHorizontally), external = true)
        }
    }
}

@Composable
private fun ReleaseCard(release: AppRelease, latest: Boolean, startOpen: Boolean) {
    val context = LocalContext.current
    var open by rememberSaveable(release.tag) { mutableStateOf(startOpen) }
    val turn by animateFloatAsState(if (open) 0f else -90f, label = "changelogChevron")
    val installed = Releases.compareVersions(release.version, BuildConfig.VERSION_NAME) == 0
    Column(Modifier.fillMaxWidth().outlinedCard()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(SpicySpacing.S4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(20.dp).rotate(turn), tint = SpicyColors.TextSecondary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                    Text("v${release.version}", style = SpicyType.Headline)
                    if (latest) Badge("Latest", SpicyColors.StatusSuccess)
                    if (installed) Badge("Installed", SpicyColors.StatusInfo)
                }
                summary(release).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = SpicyType.Footnote.copy(color = SpicyColors.TextSecondary))
                }
            }
        }
        AnimatedVisibility(open) {
            Column(
                Modifier.padding(start = SpicySpacing.S4, end = SpicySpacing.S4, bottom = SpicySpacing.S4),
                verticalArrangement = Arrangement.spacedBy(SpicySpacing.S4),
            ) {
                if (release.notes.isEmpty) {
                    Text("No notes for this version.", style = SpicyType.Body.copy(color = SpicyColors.TextSecondary))
                } else ReleaseNotesView(release.notes)
                if (release.pageUrl.isNotEmpty()) SpicyButton("View on GitHub", { openUrl(context, release.pageUrl) }, external = true)
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        style = SpicyType.Footnote.copy(color = color, fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .clip(RoundedCornerShape(SpicyRadii.Sm))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** "29 Oct 2026 · 1 new · 3 changed · 16 fixed". */
private fun summary(release: AppRelease): String {
    val date = release.date?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull() }
    return listOfNotNull(date, changeCounts(release.notes).takeIf { it.isNotEmpty() }).joinToString(" · ")
}

/** "1 new · 3 changed · 16 fixed", leaving out kinds with none. */
internal fun changeCounts(notes: ReleaseNotes): String =
    listOf(ReleaseNotes.Kind.New to "new", ReleaseNotes.Kind.Changed to "changed", ReleaseNotes.Kind.Fixed to "fixed")
        .mapNotNull { (kind, word) ->
            notes.sections.filter { it.kind == kind }.sumOf { it.entries.size }.takeIf { it > 0 }?.let { "$it $word" }
        }
        .joinToString(" · ")

private const val RELEASES_PAGE = "https://github.com/spicylyrics/mobile/releases"
