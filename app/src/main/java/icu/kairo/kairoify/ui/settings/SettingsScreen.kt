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
package icu.kairo.kairoify.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Settings: switch each source on or off, and add API keys for the ones that need them. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val links by viewModel.links.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "title") {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
        }
        item(key = "sound") {
            SoundSection()
            HorizontalDivider()
        }
        item(key = "servers") {
            ServersSection(servers, check, onAdd = viewModel::addServer, onRemove = viewModel::removeServer, onEdit = viewModel::clearCheck)
            HorizontalDivider()
        }
        item(key = "links") {
            LinksSection(links, onAdd = viewModel::addLink, onRemove = viewModel::removeLink)
            HorizontalDivider()
        }
        item(key = "sources") {
            Column(Modifier.padding(16.dp)) {
                Text("Sources", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Switch any source on or off. Search and Home use the ones that are on. Favorites from a switched-off source still play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(rows, key = { it.id }) { row ->
            SourceRowView(row, onToggle = { viewModel.setOn(row.id, it) }, onSaveKey = { name, key -> viewModel.saveKey(name, key) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun SourceRowView(row: SourceRow, onToggle: (Boolean) -> Unit, onSaveKey: (String, String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall)
                Text(row.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    row.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = row.switchedOn, onCheckedChange = onToggle)
        }
        row.keyName?.let { keyName ->
            var draft by remember(row.key) { mutableStateOf(row.key) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSaveKey(keyName, draft) }, enabled = draft.trim() != row.key) { Text("Save") }
            }
        }
    }
}
