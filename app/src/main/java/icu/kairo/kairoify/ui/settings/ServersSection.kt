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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import icu.kairo.kairoify.source.protocols.LinkSource
import icu.kairo.kairoify.source.protocols.Server
import icu.kairo.kairoify.source.protocols.ServerKind

/** Links: web addresses kept on Home (playlists, feeds, streams, files). */
@Composable
fun LinksSection(links: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Links", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Save any web address to keep it on Home: an M3U, PLS or XSPF playlist, a podcast or artist RSS feed, a live stream, or an audio file. " +
                "You can also paste one straight into search.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onAdd(draft.trim()); draft = "" },
                enabled = LinkSource.isWebAddress(draft.trim()),
            ) { Text("Add") }
        }
        links.forEach { url ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onRemove(url) }) { Text("Remove") }
            }
        }
    }
}

/** Your servers: Subsonic/OpenSubsonic/Ampache and WebDAV, checked before they're saved. */
@Composable
fun ServersSection(
    servers: List<Server>,
    check: ServerCheck,
    onAdd: (Server, onSaved: () -> Unit) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: () -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(ServerKind.SUBSONIC) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Your servers", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Your own music, searched alongside everything else. The phone talks to the server directly; the password is kept only " +
                "in this app's storage and left out of backups. Media servers on your Wi-Fi (UPnP/DLNA) are found without adding them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        servers.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                    Text("${s.kind.label} · ${s.url}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onRemove(s.id) }) { Text("Remove") }
            }
        }
        if (!adding) {
            TextButton(onClick = { adding = true }) { Text("Add a server") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServerKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k; onEdit() }, label = { Text(when (k) { ServerKind.SUBSONIC -> "Subsonic / Ampache"; ServerKind.WEBDAV -> "WebDAV"; ServerKind.FUNKWHALE -> "Funkwhale" }) })
                }
            }
            Text(
                when (kind) {
                    ServerKind.SUBSONIC -> "Navidrome, Gonic, Airsonic, LMS, Ampache… Use the address you open it at, e.g. https://music.example.com"
                    ServerKind.WEBDAV -> "A folder's WebDAV address, e.g. https://cloud.example.com/remote.php/dav/files/you/Music"
                    ServerKind.FUNKWHALE -> "The pod's address, e.g. https://pod.example.com. Leave the user name empty; for a private pod, put an application token " +
                        "(the pod's Settings → Applications) in the password field."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (kind == ServerKind.SUBSONIC) {
                TextButton(onClick = { name = "Dogmazic"; url = DOGMAZIC_URL; onEdit() }) { Text("Use Dogmazic (free account at play.dogmazic.net)") }
            }
            val field = Modifier.fillMaxWidth()
            OutlinedTextField(name, { name = it; onEdit() }, label = { Text("Name") }, singleLine = true, modifier = field)
            OutlinedTextField(url, { url = it; onEdit() }, label = { Text("Address") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = field)
            OutlinedTextField(user, { user = it; onEdit() }, label = { Text("User name") }, singleLine = true, modifier = field)
            OutlinedTextField(password, { password = it; onEdit() }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = field)
            when (check) {
                is ServerCheck.Failed -> Text(check.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                ServerCheck.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
                ServerCheck.Idle -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = check != ServerCheck.Checking && LinkSource.isWebAddress(url.trim()),
                    onClick = {
                        val server = Server(kind, name.trim().ifBlank { url.trim().substringAfter("//").substringBefore('/') }, url.trim(), user.trim(), password)
                        onAdd(server) { adding = false; name = ""; url = ""; user = ""; password = "" }
                    },
                ) { Text("Check and save") }
                TextButton(onClick = { adding = false; onEdit() }) { Text("Cancel") }
            }
        }
    }
}

/** Dogmazic: tens of thousands of freely licensed tracks on Ampache; free sign-up, then it works like any Ampache server. */
private const val DOGMAZIC_URL = "https://play.dogmazic.net"
