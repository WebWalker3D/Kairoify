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
package icu.kairo.kairoify.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import icu.kairo.kairoify.BuildConfig

@Composable
fun AboutScreen(viewModel: AboutViewModel, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val engine by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Kairoify", style = MaterialTheme.typography.headlineMedium)
        Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("The encyclopedia of free music. Open-source radio.")
        HorizontalDivider()
        Text(Attribution.PHRASE, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(Attribution.COPYRIGHT)
        Text(
            Attribution.URL,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uri.openUri(Attribution.URL) },
        )
        Text("Licensed under the ${Attribution.LICENSE}.")
        HorizontalDivider()
        Text("Station directory by Radio Browser — radio-browser.info")
        Text("Songs from YouTube Music, SoundCloud and Bandcamp, played through yt-dlp (yt-dlp.org) and ytmusicapi.")
        HorizontalDivider()
        Text(engine.summary)
        engine.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::checkForUpdate, enabled = !engine.checking) { Text("Check for update") }
            if (engine.canUseBuiltIn) TextButton(onClick = viewModel::useBuiltIn) { Text("Use built-in version") }
        }
        HorizontalDivider()
        Text("No ads. No tracking. No accounts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
