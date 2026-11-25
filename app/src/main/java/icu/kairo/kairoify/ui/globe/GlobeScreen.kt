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
package icu.kairo.kairoify.ui.globe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.source.radiobrowser.PlacedStation
import kotlin.math.hypot
import kotlin.math.min

/** A spinning globe of live radio: every dot is a station. Drag to turn it, tap a dot to hear it. */
@Composable
fun GlobeScreen(viewModel: GlobeViewModel, onPlay: (Item) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var centerLat by remember { mutableDoubleStateOf(30.0) }
    var centerLon by remember { mutableDoubleStateOf(10.0) }
    var picked by remember { mutableStateOf<PlacedStation?>(null) }
    val ocean = MaterialTheme.colorScheme.surfaceVariant
    val rim = MaterialTheme.colorScheme.outline
    val dot = MaterialTheme.colorScheme.primary
    val hot = MaterialTheme.colorScheme.tertiary
    Column(Modifier.fillMaxSize()) {
        Text("Radio globe", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        Text(
            "Every dot is a live station. Drag to turn the world; tap a dot to listen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!)
                    Button(onClick = viewModel::retry) { Text("Try again") }
                }
                else -> {
                    val stations = state.stations
                    Canvas(
                        Modifier.fillMaxSize().padding(12.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val radius = min(size.width, size.height) / 2f
                                    val degPerPx = 90.0 / radius
                                    centerLon = wrapLon(centerLon - drag.x * degPerPx)
                                    centerLat = (centerLat + drag.y * degPerPx).coerceIn(-80.0, 80.0)
                                }
                            }
                            .pointerInput(stations) {
                                detectTapGestures { tap ->
                                    val radius = min(size.width, size.height) / 2f
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    picked = stations.mapNotNull { s ->
                                        val p = project(s.lat, s.lon, centerLat, centerLon)
                                        if (!p.visible) return@mapNotNull null
                                        val d = hypot(c.x + p.x * radius - tap.x, c.y - p.y * radius - tap.y)
                                        if (d <= 28.dp.toPx()) s to d else null
                                    }.minByOrNull { it.second }?.first ?: picked
                                }
                            },
                    ) {
                        val radius = min(size.width, size.height) / 2f
                        drawCircle(ocean, radius, center)
                        drawCircle(rim, radius, center, style = Stroke(1.dp.toPx()))
                        val r = 1.6.dp.toPx()
                        stations.forEachIndexed { i, s ->
                            val p = project(s.lat, s.lon, centerLat, centerLon)
                            if (p.visible) {
                                drawCircle(if (i < 200) hot else dot.copy(alpha = 0.7f), if (i < 200) r * 1.6f else r, Offset(center.x + p.x * radius, center.y - p.y * radius))
                            }
                        }
                        picked?.let { s ->
                            val p = project(s.lat, s.lon, centerLat, centerLon)
                            if (p.visible) drawCircle(Color.White, 6.dp.toPx(), Offset(center.x + p.x * radius, center.y - p.y * radius), style = Stroke(2.dp.toPx()))
                        }
                    }
                }
            }
        }
        picked?.let { s ->
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(s.station.title, style = MaterialTheme.typography.titleMedium)
                    s.station.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Button(onClick = { onPlay(s.station) }, modifier = Modifier.padding(top = 8.dp)) { Text("Listen") }
                }
            }
        }
    }
}
