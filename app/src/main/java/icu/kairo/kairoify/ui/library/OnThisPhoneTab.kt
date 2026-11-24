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
package icu.kairo.kairoify.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.local.LocalMusicSource
import icu.kairo.kairoify.ui.components.ItemRow

/** The music files on this phone, after asking for access once. */
@Composable
internal fun OnThisPhoneTab(
    source: LocalMusicSource,
    onPlayQueue: (items: List<Item>, startIndex: Int, shuffle: Boolean) -> Unit,
    onLongPress: (Item) -> Unit,
) {
    var granted by remember { mutableStateOf(source.hasAccess()) }
    var songs by remember { mutableStateOf<List<Track>?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(granted) { if (granted) songs = source.allSongs() }
    Column(Modifier.fillMaxSize()) {
        when {
            !granted -> Column(Modifier.padding(16.dp)) {
                Text(
                    "Play the music files on this phone alongside everything streamed. Kairoify only reads them; nothing leaves the phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { ask.launch(LocalMusicSource.PERMISSION) }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Allow access to music files")
                }
            }
            songs == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            songs!!.isEmpty() -> Text(
                "No music files found on this phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            else -> {
                val list = songs!!
                PlayButtons(list, onPlayQueue)
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, t -> t.ref.itemId }) { i, track ->
                        ItemRow(track, onClick = { onPlayQueue(list, i, false) }, onLongClick = onLongPress, badge = track.album)
                    }
                }
            }
        }
    }
}
