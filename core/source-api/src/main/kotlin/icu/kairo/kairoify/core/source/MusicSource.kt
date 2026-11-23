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
package icu.kairo.kairoify.core.source

import icu.kairo.kairoify.core.model.BrowseNode
import icu.kairo.kairoify.core.model.Facet
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf

data class SourceInfo(val id: String, val displayName: String)

enum class Capability { HOME, SEARCH, BROWSE, LIVE, RELATED }

/** One free music service. Every service in Kairoify plugs in through this contract. */
interface MusicSource {
    val info: SourceInfo
    val capabilities: Set<Capability>

    /** What this source's search returns, so search can file its results under Songs or Stations. */
    val searchKind: ItemKind

    /** How long a search may take before it counts as failed; null uses the registry's default. */
    val searchTimeoutMillis: Long? get() = null

    suspend fun home(): List<Shelf> = emptyList()

    suspend fun search(query: String, page: PageToken? = null): Page<Item> = Page.empty()

    suspend fun facets(kind: FacetKind): List<Facet> = emptyList()

    suspend fun browse(node: BrowseNode, page: PageToken? = null): Page<Item> = Page.empty()

    /** Items to play after [ref] ("Start radio"). Only sources with [Capability.RELATED] return anything. */
    suspend fun related(ref: MediaRef): List<Item> = emptyList()

    suspend fun resolve(ref: MediaRef): ResolvedStream

    suspend fun reportPlay(ref: MediaRef) {}
}
