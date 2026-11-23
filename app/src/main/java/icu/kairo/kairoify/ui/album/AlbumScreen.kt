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
package icu.kairo.kairoify.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.ui.components.Artwork
import icu.kairo.kairoify.ui.components.ItemRow
import icu.kairo.kairoify.ui.components.SectionError
import icu.kairo.kairoify.ui.icons.KairoifyIcons
import icu.kairo.kairoify.ui.player.FavoriteButton

/** A concert or album: header, ♥, Play all, Shuffle, and its tracks. */
@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel,
    isFavorite: Boolean,
    onToggleFavorite: (Item) -> Unit,
    onPlayQueue: (items: List<Item>, startIndex: Int, shuffle: Boolean) -> Unit,
    onLongPress: (Item) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        AlbumUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is AlbumUiState.Failed -> SectionError(s.message, viewModel::retry)
        is AlbumUiState.Loaded -> LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(s.album.artworkUrl, s.album.title, 120.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.album.title, style = MaterialTheme.typography.titleLarge)
                            s.album.subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(
                                if (s.tracks.size == 1) "1 track" else "${s.tracks.size} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FavoriteButton(isFavorite, onToggle = { onToggleFavorite(s.album) })
                    }
                    s.album.license?.let { license ->
                        val url = license.url
                        if (url != null) TextButton(onClick = { onOpenLink(url) }) { Text("License: ${license.name}") } else Text("License: ${license.name}")
                    }
                    if (s.tracks.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { onPlayQueue(s.tracks, 0, false) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play all")
                            }
                            OutlinedButton(onClick = { onPlayQueue(s.tracks, 0, true) }) {
                                Icon(KairoifyIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Shuffle")
                            }
                        }
                    } else {
                        Text("No playable tracks in this one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    s.album.pageUrl?.let { url -> TextButton(onClick = { onOpenLink(url) }) { Text("Open original") } }
                }
            }
            itemsIndexed(s.tracks, key = { _, t -> t.ref.encode() }) { index, track ->
                ItemRow(track, onClick = { onPlayQueue(s.tracks, index, false) }, onLongClick = onLongPress)
            }
        }
    }
}
