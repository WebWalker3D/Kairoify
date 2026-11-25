/*
 * Copyright (c) 2026 WebWalker3D. All Rights Reserved.
 *
 * The contents of this file are subject to the Common Public Attribution License
 * Version 1.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * https://github.com/WebWalker3D/Kairoify/blob/main/LICENSE. The License is based on
 * the Mozilla Public License Version 1.1 but Sections 14 and 15 have been added to
 * cover use of software over a computer network and provide for limited attribution
 * for the Original Developer. In addition, Exhibit A has been modified to be
 * consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT
 * WARRANTY OF ANY KIND, either express or implied. See the License for the specific
 * language governing rights and limitations under the License.
 *
 * The Original Code is Kairoify. The Initial Developer of the Original Code is
 * WebWalker3D. Attribution information: see NOTICE.md.
 */
package icu.kairo.kairoify.ui.player

import icu.kairo.kairoify.player.UpNextEntry
import icu.kairo.kairoify.player.StationLabel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.player.NowPlaying
import icu.kairo.kairoify.player.PlayerConnection
import icu.kairo.kairoify.source.lyrics.Lyrics
import androidx.compose.runtime.saveable.rememberSaveable
import icu.kairo.kairoify.ui.components.Artwork
import icu.kairo.kairoify.ui.components.formatDuration
import icu.kairo.kairoify.ui.icons.KairoifyIcons
import kotlinx.coroutines.delay

/** The full player: artwork, seek bar for songs, queue controls, ♥, and links. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    nowPlaying: NowPlaying,
    sourceName: String,
    player: PlayerConnection,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onOpenLink: (String) -> Unit,
    onStopped: () -> Unit,
    onDismiss: () -> Unit,
    extraActions: @Composable ColumnScope.() -> Unit = {},
    station: StationLabel? = null,
    upNext: List<UpNextEntry> = emptyList(),
    /** Looks up the song's lyrics; null when this item can't have any (live radio). */
    findLyrics: (suspend () -> Lyrics?)? = null,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            station?.let { Text(it.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            Artwork(nowPlaying.artworkUrl, nowPlaying.title, 240.dp)
            if (nowPlaying.isLive) LiveBadge()
            Text(nowPlaying.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            (nowPlaying.errorMessage ?: nowPlaying.subtitle)?.let {
                Text(
                    it,
                    textAlign = TextAlign.Center,
                    color = if (nowPlaying.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            nowPlaying.license?.let { license ->
                val url = license.url
                if (url != null) {
                    TextButton(onClick = { onOpenLink(url) }) { Text("License: ${license.name}") }
                } else {
                    Text("License: ${license.name}", style = MaterialTheme.typography.labelMedium)
                }
            }
            nowPlaying.durationMs?.let { SeekBar(nowPlaying, it, player) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!nowPlaying.isLive) {
                    ToggleIcon(KairoifyIcons.Shuffle, "Shuffle", on = nowPlaying.shuffle, onClick = player::toggleShuffle)
                }
                IconButton(onClick = player::skipPrevious, enabled = nowPlaying.hasPrevious) {
                    Icon(KairoifyIcons.SkipPrevious, contentDescription = "Previous")
                }
                FilledIconButton(onClick = player::togglePlayPause, modifier = Modifier.size(72.dp)) {
                    if (nowPlaying.isBuffering) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(
                            if (nowPlaying.isPlaying) KairoifyIcons.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                IconButton(onClick = player::skipNext, enabled = nowPlaying.hasNext) {
                    Icon(KairoifyIcons.SkipNext, contentDescription = "Next")
                }
                if (!nowPlaying.isLive) {
                    val one = nowPlaying.repeatMode == Player.REPEAT_MODE_ONE
                    ToggleIcon(
                        if (one) KairoifyIcons.RepeatOne else KairoifyIcons.Repeat,
                        if (one) "Repeat one" else "Repeat",
                        on = nowPlaying.repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = player::cycleRepeat,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                onToggleFavorite?.let { FavoriteButton(isFavorite, it) }
                IconButton(onClick = {
                    player.stop()
                    onStopped()
                }) { Icon(KairoifyIcons.Stop, contentDescription = "Stop") }
            }
            extraActions()
            findLyrics?.let { load ->
                TextButton(onClick = { showLyrics = !showLyrics }) { Text(if (showLyrics) "Hide lyrics" else "Lyrics") }
                if (showLyrics) LyricsPanel(nowPlaying.ref, player, load)
            }
            val link = (nowPlaying.item as? Track)?.pageUrl?.let { "Open original" to it }
                ?: nowPlaying.homepage?.let { "Open station website" to it }
            link?.let { (label, url) -> TextButton(onClick = { onOpenLink(url) }) { Text(label) } }
            Spacer(Modifier.height(4.dp))
            Text("via $sourceName", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            UpNext(upNext, building = station?.building == true, onSkipTo = player::skipTo)
        }
    }
}

/** Song position with drag-to-seek; polls the player twice a second while the sheet is open. */
@Composable
private fun SeekBar(nowPlaying: NowPlaying, durationMs: Long, player: PlayerConnection) {
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(nowPlaying.ref) {
        while (true) {
            if (dragging == null) position = player.position()
            delay(500)
        }
    }
    val fraction = dragging ?: (position.toFloat() / durationMs).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { player.seekTo((it * durationMs).toLong()) }
                dragging = null
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration((fraction * durationMs).toLong()), style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Up next": the coming songs, tap one to jump to it; while a station is still being built, says so. */
@Composable
private fun UpNext(entries: List<UpNextEntry>, building: Boolean, onSkipTo: (Int) -> Unit) {
    if (entries.isEmpty() && !building) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Up next", style = MaterialTheme.typography.titleMedium)
        entries.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().clickable { onSkipTo(entry.index) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(entry.artworkUrl, entry.title, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    entry.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (building) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Finding similar music…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
