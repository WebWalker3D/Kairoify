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

import icu.kairo.kairoify.core.model.BrowseNode
import icu.kairo.kairoify.core.model.FacetKind
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object SearchRoute

@Serializable data object ExploreRoute

@Serializable data object AboutRoute

@Serializable data object LibraryRoute

@Serializable data object SettingsRoute

@Serializable data object GlobeRoute

@Serializable data class PlaylistRoute(val id: Long)

@Serializable data class CategoryRoute(val category: icu.kairo.kairoify.settings.SourceCategory)

@Serializable data class AlbumRoute(val sourceId: String, val itemId: String, val title: String)

@Serializable data class FacetsRoute(val kind: FacetKind, val sourceId: String = "radiobrowser")

@Serializable data class BrowseRoute(val sourceId: String, val nodeType: String, val nodeValue: String, val title: String) {
    fun node(): BrowseNode = when (nodeType) {
        "country" -> BrowseNode.Country(nodeValue)
        "genre" -> BrowseNode.Genre(nodeValue)
        "collection" -> BrowseNode.Collection(nodeValue)
        else -> error("Unknown browse node type '$nodeType'")
    }

    companion object {
        fun of(sourceId: String, node: BrowseNode, title: String): BrowseRoute = when (node) {
            is BrowseNode.Country -> BrowseRoute(sourceId, "country", node.code, title)
            is BrowseNode.Genre -> BrowseRoute(sourceId, "genre", node.name, title)
            is BrowseNode.Collection -> BrowseRoute(sourceId, "collection", node.id, title)
        }
    }
}
