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
package icu.kairo.kairoify.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.ui.components.ShelfRow

/**
 * Search results as one row of cover art per source, swiped sideways. Sources with nothing are hidden; sources
 * still searching share one line; sources that failed share one line with a single Retry.
 */
fun LazyListScope.sourceRows(
    results: SearchUiState.Results,
    onPlay: (Item) -> Unit,
    onLongPress: (Item) -> Unit,
    onRetry: (String) -> Unit,
) {
    val loaded = results.sections.mapNotNull { s -> (s.state as? SearchSectionState.Loaded)?.takeIf { it.items.isNotEmpty() }?.let { s to it.items } }
    val loading = results.sections.filter { it.state is SearchSectionState.Loading }
    val failed = results.sections.filter { it.state is SearchSectionState.Failed }
    loaded.forEach { (section, items) ->
        item(key = "row-${section.source.id}") {
            ShelfRow(section.source.displayName, items, onItemClick = onPlay, onItemLongClick = onLongPress)
        }
    }
    if (loading.isNotEmpty()) {
        item(key = "loading") {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                Text(
                    "Still searching " + loading.joinToString(", ") { it.source.displayName } + "…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (failed.isNotEmpty()) {
        item(key = "failed") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    failed.joinToString(", ") { it.source.displayName } + if (failed.size == 1) " didn't answer." else " didn't answer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { failed.forEach { onRetry(it.source.id) } }) { Text("Retry") }
            }
        }
    }
    if (loaded.isEmpty() && loading.isEmpty()) {
        item(key = "nothing") {
            Text(
                "Nothing found for “${results.query}”.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
