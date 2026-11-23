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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import icu.kairo.kairoify.player.NowPlaying
import icu.kairo.kairoify.ui.components.Artwork
import icu.kairo.kairoify.ui.icons.KairoifyIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    nowPlaying: NowPlaying,
    sourceName: String,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onStop) { Icon(KairoifyIcons.Stop, contentDescription = "Stop") }
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
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
                Spacer(Modifier.size(48.dp))
            }
            nowPlaying.homepage?.let { home ->
                TextButton(onClick = { uri.openUri(home) }) { Text("Open station website") }
            }
            Spacer(Modifier.height(4.dp))
            Text("via $sourceName", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
