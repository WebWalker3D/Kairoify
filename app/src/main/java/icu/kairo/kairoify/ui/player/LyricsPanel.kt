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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import icu.kairo.kairoify.player.PlayerConnection
import icu.kairo.kairoify.source.lyrics.Lyrics
import kotlinx.coroutines.delay

/** Looks up lyrics once per song; the line being sung is highlighted and nearby lines shown. Tap a line to jump there. */
@Composable
internal fun LyricsPanel(key: Any, player: PlayerConnection, load: suspend () -> Lyrics?) {
    var state by remember(key) { mutableStateOf<LyricsState>(LyricsState.Loading) }
    var position by remember(key) { mutableLongStateOf(0L) }
    LaunchedEffect(key) { state = load()?.let { LyricsState.Found(it) } ?: LyricsState.None }
    val lyrics = (state as? LyricsState.Found)?.lyrics
    if (lyrics?.isSynced == true) {
        LaunchedEffect(key) {
            while (true) {
                position = player.position()
                delay(300)
            }
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            state is LyricsState.Loading -> CircularProgressIndicator()
            lyrics == null -> Text("No lyrics found for this song.", color = muted)
            lyrics.instrumental -> Text("Instrumental", color = muted)
            lyrics.isSynced -> {
                val now = lyrics.lineAt(position)
                val from = (now - 2).coerceAtLeast(0)
                lyrics.lines.subList(from, (from + 7).coerceAtMost(lyrics.lines.size)).forEachIndexed { i, line ->
                    val current = from + i == now
                    Text(
                        line.text.ifEmpty { "♪" },
                        textAlign = TextAlign.Center,
                        style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = if (current) MaterialTheme.colorScheme.primary else muted,
                        modifier = Modifier.clickable { player.seekTo(line.atMillis) },
                    )
                }
            }
            else -> Text(lyrics.plain.orEmpty(), textAlign = TextAlign.Center, color = muted)
        }
        Text("Lyrics from LRCLIB", style = MaterialTheme.typography.labelSmall, color = muted)
    }
}

private sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
}
