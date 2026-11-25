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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import icu.kairo.kairoify.core.player.SoundSettings

/** Even volume, the equalizer, and the phone's own sound panel. */
@Composable
internal fun SoundSection() {
    val context = LocalContext.current
    val sound = remember { SoundSettings(context) }
    var even by remember { mutableStateOf(sound.evenVolume) }
    var preset by remember { mutableIntStateOf(sound.equalizerPreset) }
    var menu by remember { mutableStateOf(false) }
    val presets = remember { sound.presetNames }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Sound", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Even out volume", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Brings quiet recordings up and loud ones down, so old 78s, live tapes and new songs sit together.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = even, onCheckedChange = { even = it; sound.evenVolume = it })
            }
        }
        Column(Modifier.fillMaxWidth().clickable(enabled = presets.isNotEmpty()) { menu = true }.padding(vertical = 10.dp)) {
            Text("Equalizer", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    presets.isEmpty() -> "Play something once, then choose a preset here."
                    preset < 0 -> "Off"
                    else -> presets.getOrElse(preset) { "Off" }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Off") }, onClick = { preset = -1; sound.equalizerPreset = -1; menu = false })
                presets.forEachIndexed { i, name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { preset = i; sound.equalizerPreset = i; menu = false })
                }
            }
        }
        TextButton(onClick = {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sound.audioSession)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                android.widget.Toast.makeText(context, "This phone has no sound settings panel for apps.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }) { Text("Phone's sound settings") }
    }
}
