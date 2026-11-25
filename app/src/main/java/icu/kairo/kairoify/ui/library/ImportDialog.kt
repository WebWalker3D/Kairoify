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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Paste a link or a song list, then watch each song being found. */
@Composable
internal fun ImportDialog(viewModel: PlaylistsViewModel, onClose: () -> Unit, onOpenPlaylist: (Long) -> Unit) {
    val state by viewModel.importState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var input by rememberSaveable { mutableStateOf("") }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = { if (state !is ImportState.Working) onClose() },
        title = { Text("Import a playlist") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    ImportState.Idle, is ImportState.Failed -> {
                        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Playlist name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            input, { input = it },
                            label = { Text("Link, or one song per line") },
                            placeholder = { Text("Nina Simone - Feeling Good") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        )
                        Text(
                            "Each song is looked up on your free sources. Spotify and Apple Music links can't be read; paste their song list instead.",
                            style = MaterialTheme.typography.bodySmall, color = muted,
                        )
                        if (s is ImportState.Failed) Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
                    is ImportState.Working -> {
                        Text(if (s.total == 0) "Reading the playlist…" else "Finding songs: ${s.done} of ${s.total}")
                        if (s.total == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { s.done.toFloat() / s.total }, modifier = Modifier.fillMaxWidth())
                    }
                    is ImportState.Done -> {
                        Text(if (s.added == 0) "None of those songs were found on your sources." else "Added ${s.added} songs.")
                        if (s.missed.isNotEmpty()) {
                            Text("Not found (${s.missed.size}):", style = MaterialTheme.typography.labelLarge)
                            s.missed.take(30).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                            if (s.missed.size > 30) Text("…and ${s.missed.size - 30} more", style = MaterialTheme.typography.bodySmall, color = muted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                ImportState.Idle, is ImportState.Failed ->
                    TextButton(onClick = { viewModel.import(name.ifBlank { "Imported playlist" }, input) }, enabled = input.isNotBlank()) { Text("Import") }
                is ImportState.Working -> {}
                is ImportState.Done ->
                    if (s.playlistId != null) TextButton(onClick = { onOpenPlaylist(s.playlistId) }) { Text("Open playlist") }
                    else TextButton(onClick = onClose) { Text("Close") }
            }
        },
        dismissButton = { if (state !is ImportState.Working && state !is ImportState.Done) TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
