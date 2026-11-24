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
package icu.kairo.kairoify.ui.explore

import icu.kairo.kairoify.settings.SourceCategory
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.ui.icons.KairoifyIcons

@Composable
fun ExploreScreen(onOpenFacets: (FacetKind) -> Unit, onOpenArchives: () -> Unit, onOpenCategory: (SourceCategory) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Explore", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        SourceCategory.entries.forEach { category ->
            ListItem(
                supportingContent = { Text(category.description) },
                leadingContent = { Icon(categoryIcon(category), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onOpenCategory(category) },
            ) { Text(category.label) }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ListItem(
            supportingContent = { Text("Live radio from every country on Earth") },
            leadingContent = { Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { onOpenFacets(FacetKind.COUNTRY) },
        ) { Text("Countries") }
        ListItem(
            supportingContent = { Text("Jazz, news, classical, metal and thousands more") },
            leadingContent = { Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { onOpenFacets(FacetKind.GENRE) },
        ) { Text("Genres") }
        ListItem(
            supportingContent = { Text("Live concerts, 78 rpm records, netlabels and classical from the Internet Archive") },
            leadingContent = { Icon(KairoifyIcons.Library, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable(onClick = onOpenArchives),
        ) { Text("Concerts & archives") }
    }
}

/** The icon each section is shown with. */
fun categoryIcon(category: SourceCategory): ImageVector = when (category) {
    SourceCategory.MUSIC -> Icons.Filled.PlayArrow
    SourceCategory.RADIO -> KairoifyIcons.Radio
    SourceCategory.AUDIOBOOKS -> Icons.Filled.Create
    SourceCategory.PODCASTS -> Icons.Filled.Person
    SourceCategory.HISTORY -> Icons.Filled.DateRange
    SourceCategory.SOUNDS -> Icons.Filled.Notifications
}
