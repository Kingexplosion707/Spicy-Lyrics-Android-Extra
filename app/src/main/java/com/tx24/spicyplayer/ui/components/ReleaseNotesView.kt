package com.tx24.spicyplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import com.tx24.spicyplayer.update.Inline
import com.tx24.spicyplayer.update.ReleaseNotes

/**
 * A release's notes as the release page lays them out: each section under its heading (New in
 * green, Changed in blue, Fixed in amber), each bullet's bold lead in white and the rest in grey.
 * Links open in the browser.
 */
@Composable
fun ReleaseNotesView(notes: ReleaseNotes, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SpicySpacing.S4)) {
        notes.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                section.title?.let { SectionHeading(it, kindColor(section.kind)) }
                section.entries.forEach { EntryRow(it) }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(title, style = SpicyType.Footnote.copy(color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em))
    }
}

@Composable
private fun EntryRow(entry: ReleaseNotes.Entry) {
    val style = SpicyType.Body.copy(color = SpicyColors.TextSecondary, lineHeight = 1.5.em)
    Row {
        Text("•", style = style, modifier = Modifier.width(20.dp).padding(start = 6.dp))
        Text(entryText(entry), style = style, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun entryText(entry: ReleaseNotes.Entry): AnnotatedString {
    val lead = SpanStyle(color = SpicyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
    val links = TextLinkStyles(SpanStyle(color = SpicyColors.TextPrimary, textDecoration = TextDecoration.Underline))
    val code = SpanStyle(fontFamily = FontFamily.Monospace, background = SpicyColors.TintBg)
    return buildAnnotatedString {
        // [base] for the lead; in the body, bold reads like the lead.
        fun marked(text: String, base: SpanStyle?) = Inline.spans(text).forEach { span ->
            val style = listOfNotNull(
                base,
                lead.takeIf { span.bold && base == null },
                code.takeIf { span.code },
            ).fold(SpanStyle()) { acc, next -> acc.merge(next) }
            val url = span.url
            withStyle(style) {
                if (url != null) withLink(LinkAnnotation.Url(url, links)) { append(span.text) } else append(span.text)
            }
        }
        entry.lead?.let { marked(it, lead) }
        if (entry.lead != null && entry.body.isNotEmpty()) append(" ")
        marked(entry.body, null)
    }
}

@Composable
private fun kindColor(kind: ReleaseNotes.Kind): Color = when (kind) {
    ReleaseNotes.Kind.New -> SpicyColors.StatusSuccess
    ReleaseNotes.Kind.Changed -> SpicyColors.StatusInfo
    ReleaseNotes.Kind.Fixed -> SpicyColors.StatusWarning
    ReleaseNotes.Kind.Other -> SpicyColors.TextTertiary
}
