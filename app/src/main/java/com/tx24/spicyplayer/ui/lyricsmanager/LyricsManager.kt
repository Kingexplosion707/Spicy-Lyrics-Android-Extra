package com.tx24.spicyplayer.ui.lyricsmanager

import com.tx24.spicyplayer.ui.components.screenSlide

import com.tx24.spicyplayer.ui.theme.SpicyMotion

import com.tx24.spicyplayer.ui.components.LocalUiAnimations

import androidx.compose.animation.togetherWith

import androidx.compose.animation.slideOutHorizontally

import androidx.compose.animation.slideInHorizontally

import androidx.compose.animation.fadeOut

import androidx.compose.animation.fadeIn

import androidx.compose.animation.animateContentSize

import androidx.compose.animation.SizeTransform

import androidx.compose.animation.ExitTransition

import androidx.compose.animation.EnterTransition

import androidx.compose.animation.AnimatedContent

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.tx24.spicyplayer.haptics.withHaptic
import com.tx24.spicyplayer.lyrics.LocalLyricsStore
import com.tx24.spicyplayer.ui.components.BlockSkeleton
import com.tx24.spicyplayer.ui.components.TrackListSkeleton
import com.tx24.spicyplayer.lyrics.LrcConverter
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.SpicyTextField
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.components.focusRing
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The Lyrics Manager: the songs you've given your own TTML, and the upload that adds one.
 * Opens on the list ("Local Lyrics DB"); Upload moves the same pop-up to the upload screen, and
 * back (or Back) returns to the list.
 */

@Composable
fun LyricsManagerModal(
    visible: Boolean,
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    settings: AppSettings,
    backdrop: HazeState?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var uploading by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            uploading = false
            viewModel.refreshLocalLyrics()
        }
    }
    SpicyModal(
        visible = visible,
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        title = if (uploading) "Upload TTML" else "Local Lyrics DB",
        onBack = if (uploading) ({ uploading = false }) else null,
        // The list decodes a cover per saved song: it comes in once the pop-up is open.
        skeleton = {
            BlockSkeleton()
            BlockSkeleton()
            BlockSkeleton(height = 56.dp)
            TrackListSkeleton(rows = 4)
        },
    ) {
        LyricsManagerContent(state, viewModel, settings, uploading, { uploading = it }, onDismissRequest)
    }
}

/**
 * The Lyrics Manager's screens, the list and the upload, for its own pop-up or quick settings.
 * [uploading] picks the screen; the upload slides in over the list and back out.
 */
@Composable
fun LyricsManagerContent(
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    settings: AppSettings,
    uploading: Boolean,
    onUploadingChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val animate = LocalUiAnimations.current
    AnimatedContent(
        targetState = uploading,
        transitionSpec = { screenSlide(forward = targetState, animate = animate) },
        label = "lyricsManagerScreen",
    ) { upload ->
        Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
            if (upload) {
                UploadScreen(
                    songName = state.title,
                    settings = settings,
                    viewModel = viewModel,
                    // Once saved, back to the list with it; a just-once upload is done.
                    onDone = { saved -> if (saved) onUploadingChange(false) else onClose() },
                )
            } else {
                LibraryScreen(state, viewModel, onUpload = { onUploadingChange(true) })
            }
        }
    }
}

@Composable
private fun LibraryScreen(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, onUpload: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    // The song being exported, while the system's save screen is up.
    var exporting by remember { mutableStateOf<LocalLyricsStore.Entry?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TTML_MIME)) { uri ->
        val entry = exporting ?: return@rememberLauncherForActivityResult
        exporting = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ttml = viewModel.localLyricsText(entry.key)
            val written = ttml != null && withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(ttml.toByteArray()) } != null }
                    .getOrDefault(false)
            }
            if (!written) viewModel.showMessage("Could not retrieve TTML for this track.")
        }
    }

    val needle = query.trim().lowercase()
    val shown = state.localLyrics
        .filter { needle.isEmpty() || needle in it.title.lowercase() || needle in it.artist.lowercase() }
        // The playing song goes first.
        .sortedByDescending { it.key == state.localLyricsKey }

    SpicyTextField(
        query,
        { query = it },
        placeholder = "Search saved lyrics…",
        modifier = Modifier.fillMaxWidth(),
        leading = { Icon(Icons.Rounded.Search, null, tint = SpicyColors.TextTertiary, modifier = Modifier.size(18.dp)) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        ToolbarButton(Icons.Rounded.RestartAlt, "Reset TTML", ToolbarStyle.Danger, viewModel::resetTtml, Modifier.weight(1f))
        ToolbarButton(Icons.Rounded.FileUpload, "Upload TTML", ToolbarStyle.Primary, onUpload, Modifier.weight(1f))
    }
    FolderCard(state, viewModel)
    if (shown.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpicySpacing.S4),
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = SpicyColors.TextSecondary, modifier = Modifier.size(40.dp))
            Text(
                if (needle.isEmpty()) "No lyrics saved yet" else "No matching entries",
                style = SpicyType.Body.copy(color = SpicyColors.TextSecondary),
            )
        }
    } else {
        Column(
            Modifier.then(if (LocalUiAnimations.current) Modifier.animateContentSize(tween(SpicyMotion.MODAL_MS)) else Modifier),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            shown.forEach { entry ->
                TrackRow(
                    entry = entry,
                    cover = viewModel.localLyricsCover(entry.key),
                    playing = entry.key == state.localLyricsKey,
                    onExport = {
                        exporting = entry
                        export.launch("${sanitizeFilename(entry.title)}.ttml")
                    },
                    onDelete = { viewModel.removeLocalLyrics(entry.key) },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    entry: LocalLyricsStore.Entry,
    cover: java.io.File,
    playing: Boolean,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    // Delete asks twice: the first tap turns it into "Confirm" for 3 s.
    var confirmDelete by remember(entry.key) { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3_000)
            confirmDelete = false
        }
    }
    val shape = RoundedCornerShape(SpicyRadii.Md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (playing) Modifier.background(SpicyColors.TintBg).border(1.dp, SpicyColors.HairlineStrong, shape)
                else Modifier,
            )
            .padding(horizontal = SpicySpacing.S3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Cover(cover, entry.savedAt)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    Box(Modifier.padding(end = 6.dp).size(6.dp).background(SpicyColors.StatusSuccess, CircleShape))
                }
                Text(
                    entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = SpicyType.Body.copy(
                        color = if (playing) SpicyColors.TextPrimary else SpicyColors.TextPrimary.copy(alpha = 0.85f),
                        fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
            }
            if (entry.artist.isNotBlank()) {
                Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary))
            }
        }
        RowAction(Icons.Rounded.FileDownload, "Download TTML", onClick = onExport)
        RowAction(
            Icons.Rounded.Delete,
            if (confirmDelete) "Confirm delete" else "Delete",
            label = if (confirmDelete) "Confirm" else null,
            danger = confirmDelete,
            onClick = {
                if (confirmDelete) {
                    confirmDelete = false
                    onDelete()
                } else confirmDelete = true
            },
        )
    }
}

/**
 * The lyrics folder: .ttml and .lrc files read where they are, picked up as they're added or
 * changed. Its songs aren't listed; a folder can hold thousands.
 */
@Composable
private fun FolderCard(state: PlayerUiState, viewModel: ExternalPlaybackViewModel) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) viewModel.linkLyricsFolder(tree)
    }
    // Unlinking asks twice, like deleting.
    var confirmUnlink by remember { mutableStateOf(false) }
    LaunchedEffect(confirmUnlink) {
        if (confirmUnlink) {
            delay(3_000)
            confirmUnlink = false
        }
    }
    val folder = state.lyricsFolder
    val shape = RoundedCornerShape(SpicyRadii.Md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SpicyColors.TintBg)
            .border(1.dp, SpicyColors.Hairline, shape)
            .padding(horizontal = SpicySpacing.S3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Icon(Icons.Rounded.Folder, null, tint = SpicyColors.TextSecondary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(folder?.name ?: "Lyrics folder", maxLines = 1, overflow = TextOverflow.Ellipsis, style = SpicyType.Body.copy(fontWeight = FontWeight.Medium))
            Text(
                when {
                    folder == null -> "Read .ttml and .lrc files from a folder."
                    state.lyricsFolderScanning -> "Reading…"
                    else -> "${folder.files} ${if (folder.files == 1) "file" else "files"}"
                },
                maxLines = 2,
                style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary),
            )
        }
        if (folder == null) {
            RowAction(Icons.Rounded.Folder, "Choose a lyrics folder", label = "Choose", onClick = { pick.launch(null) })
        } else {
            if (state.lyricsFolderScanning) {
                CircularProgressIndicator(Modifier.size(18.dp), color = SpicyColors.TextSecondary, strokeWidth = 2.dp)
            } else {
                RowAction(Icons.Rounded.Refresh, "Rescan lyrics folder", onClick = { viewModel.rescanLyricsFolder(manual = true) })
            }
            RowAction(
                Icons.Rounded.LinkOff,
                if (confirmUnlink) "Confirm unlink" else "Unlink lyrics folder",
                label = if (confirmUnlink) "Confirm" else null,
                danger = confirmUnlink,
                onClick = {
                    if (confirmUnlink) {
                        confirmUnlink = false
                        viewModel.unlinkLyricsFolder()
                    } else confirmUnlink = true
                },
            )
        }
    }
}

/** The saved cover, or a note on a tinted square when the song had none. */
@Composable
private fun Cover(file: java.io.File, version: Long) {
    val bitmap by produceState<ImageBitmap?>(null, file, version) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() }
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(44.dp).clip(shape).background(SpicyColors.TintBgPressed),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, null, Modifier.size(44.dp), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = SpicyColors.TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun UploadScreen(
    songName: String,
    settings: AppSettings,
    viewModel: ExternalPlaybackViewModel,
    onDone: (saved: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val save = settings.ttmlUploadSaves
    // Picking the file is the upload.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val name = withContext(Dispatchers.IO) { displayName(context, uri) }
            val extension = name?.substringAfterLast('.', "")?.lowercase()
            val ttml = if (extension != null && extension != "ttml" && extension != "lrc") {
                viewModel.showMessage("Only .ttml and .lrc files are supported.")
                null
            } else withContext(Dispatchers.IO) {
                val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString().removePrefix("\uFEFF") } }.getOrNull()
                when {
                    text == null -> null.also { viewModel.showMessage("Error reading the file.") }
                    extension == "lrc" || LrcConverter.isLrc(text) ->
                        LrcConverter.toTtml(text).also { if (it == null) viewModel.showMessage("That LRC file has no timed lines.") }
                    else -> text
                }
            }
            val applied = ttml != null && viewModel.importTtml(ttml, persistent = save)
            busy = false
            if (applied) onDone(save)
        }
    }

    // The two ways to apply it, as one segmented control; it reopens on the last one used.
    // Back to the list is the header's arrow.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpicyRadii.Sm))
            .background(SpicyColors.TintBg)
            .border(1.dp, SpicyColors.Hairline, RoundedCornerShape(SpicyRadii.Sm))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ModeButton(Icons.Rounded.Storage, "Save", selected = save, enabled = !busy, Modifier.weight(1f)) { settings.ttmlUploadSaves = true }
        ModeButton(Icons.Rounded.Schedule, "Just once", selected = !save, enabled = !busy, Modifier.weight(1f)) { settings.ttmlUploadSaves = false }
    }
    Text(
        if (save) "Store in the local DB — survives restarts" else "Apply to the current song only, until refresh",
        style = SpicyType.Footnote.copy(color = SpicyColors.TextTertiary),
    )
    DropZone(
        busy = busy,
        subtitle = (if (save) "Saves to Local DB for " else "Applies once to ") + songName,
        onClick = { pick.launch(PICKABLE_TYPES) },
    )
}

/** The dashed box a tap on which opens the file picker. */
@Composable
private fun DropZone(busy: Boolean, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius = SpicyRadii.Lg
    val edge = if (pressed) SpicyColors.TextTertiary else SpicyColors.HairlineStrong
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clip(RoundedCornerShape(radius))
            .background(if (pressed) SpicyColors.TintBgPressed else SpicyColors.TintBg)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = edge,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .focusRing(RoundedCornerShape(SpicyRadii.Md))
            .clickable(interaction, indication = null, enabled = !busy, role = Role.Button, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .padding(SpicySpacing.S6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(26.dp), color = SpicyColors.TextSecondary, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.FileUpload, null, tint = SpicyColors.TextSecondary, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.width(0.dp))
        Text(
            if (busy) "Uploading…" else "Tap to choose a .ttml or .lrc file",
            style = SpicyType.Body.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        )
        Text(
            subtitle,
            style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary, textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class ToolbarStyle { Primary, Danger }

@Composable
private fun ToolbarButton(icon: ImageVector, label: String, style: ToolbarStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val touching by interaction.collectIsPressedAsState()
    // A quick tap is over in a few frames: the pressed look holds a moment past it, so it shows.
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(touching) {
        if (touching) pressed = true else {
            delay(PRESS_HOLD_MS)
            pressed = false
        }
    }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(if (pressed) 120 else 260), label = "toolbarScale")
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 120 else 260), label = "toolbarPress")
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    val (fill, content) = when (style) {
        ToolbarStyle.Primary -> lerp(SpicyColors.Accent, Color.White.copy(alpha = 0.7f), press) to SpicyColors.TextOnFill
        ToolbarStyle.Danger -> SpicyColors.StatusDanger.copy(alpha = 0.16f + 0.22f * press) to SpicyColors.StatusDanger.copy(alpha = 1f)
    }
    Row(
        modifier
            .heightIn(min = 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .focusRing(RoundedCornerShape(SpicyRadii.Md))
            .clickable(interaction, indication = null, role = Role.Button, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(label, style = SpicyType.Caption.copy(color = content, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em))
    }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SpicyRadii.Sm - 3.dp)
    val color = if (selected) SpicyColors.TextPrimary else SpicyColors.TextSecondary
    Row(
        modifier
            .heightIn(min = 34.dp)
            .clip(shape)
            .background(if (selected) SpicyColors.TintBgPressed else Color.Transparent)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = withHaptic(HapticFeedbackType.SegmentTick, onClick))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Text(label, style = SpicyType.Caption.copy(color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium))
    }
}

/** A row's small icon button; [label] shows beside the icon when set, e.g. "Confirm". */
@Composable
private fun RowAction(
    icon: ImageVector,
    description: String,
    label: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    val tint = when {
        danger -> SpicyColors.StatusDanger.copy(alpha = 1f)
        pressed -> SpicyColors.TextPrimary
        else -> SpicyColors.TextSecondary
    }
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(
                when {
                    danger -> SpicyColors.StatusDanger.copy(alpha = 0.16f)
                    pressed -> SpicyColors.TintBgPressed
                    else -> SpicyColors.TintBg
                },
            )
            .focusRing(RoundedCornerShape(SpicyRadii.Md))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = description, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .padding(horizontal = if (label != null) 10.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(18.dp))
        if (label != null) Text(label, style = SpicyType.Caption.copy(color = tint, fontWeight = FontWeight.SemiBold))
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

private fun sanitizeFilename(name: String): String = name.replace(Regex("[^a-zA-Z0-9_\\- .]"), "_").take(100)

private const val TTML_MIME = "application/ttml+xml"

/** How long a tapped toolbar button keeps its pressed look after the finger lifts. */
private const val PRESS_HOLD_MS = 300L

/**
 * What the file picker offers. It filters by type, not name: a .ttml is TTML to a phone that
 * knows the extension and a nameless blob of bytes to one that doesn't, so both are asked for
 * (with plain XML), which keeps music, pictures and documents out. A .lrc has no one type:
 * Android's own table calls it `application/lrc`, file managers and other tables use the rest.
 */
private val PICKABLE_TYPES = arrayOf(
    TTML_MIME, "application/xml", "text/xml", "text/plain", "application/octet-stream",
    "application/lrc", "application/x-lrc", "text/lrc", "text/x-lrc",
)
