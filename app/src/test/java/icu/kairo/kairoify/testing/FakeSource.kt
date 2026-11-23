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
package icu.kairo.kairoify.testing

import icu.kairo.kairoify.core.model.BrowseNode
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers

fun station(id: String, source: String = "fake") = Station(
    ref = MediaRef(source, id), title = "Station $id", subtitle = null, artworkUrl = null, countryCode = null,
    tags = emptyList(), codec = null, bitrateKbps = null, homepage = null, streamUrl = "http://x/$id", isHls = false,
)

fun track(id: String, source: String = "fake") = Track(ref = MediaRef(source, id), title = "Song $id", subtitle = "Artist $id")

class FakeSource(
    id: String,
    var homeShelves: List<Shelf> = emptyList(),
    var homeDelay: Long = 0,
    var homeError: Throwable? = null,
    var searchItems: List<Item> = emptyList(),
    var searchDelay: Long = 0,
    var searchError: Throwable? = null,
    var browsePages: Map<String?, Page<Item>> = emptyMap(),
    var browseErrorOn: String? = null,
    override val capabilities: Set<Capability> = setOf(Capability.HOME, Capability.SEARCH, Capability.BROWSE),
    override val searchKind: ItemKind = ItemKind.STATION,
    var relatedItems: List<Item> = emptyList(),
    var relatedError: Throwable? = null,
) : MusicSource {
    override val info = SourceInfo(id, id.uppercase())
    var homeCalls = 0
    val searchQueries = mutableListOf<String>()
    var browseCalls = 0

    override suspend fun home(): List<Shelf> {
        homeCalls++
        delay(homeDelay)
        homeError?.let { throw it }
        return homeShelves
    }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        searchQueries += query
        delay(searchDelay)
        searchError?.let { throw it }
        return Page(searchItems, null)
    }

    override suspend fun browse(node: BrowseNode, page: PageToken?): Page<Item> {
        browseCalls++
        if (browseErrorOn != null && browseErrorOn == page?.value) throw java.io.IOException("nope")
        return browsePages[page?.value] ?: Page.empty()
    }

    override suspend fun related(ref: MediaRef): List<Item> {
        relatedError?.let { throw it }
        return relatedItems
    }

    override suspend fun resolve(ref: MediaRef) = ResolvedStream(url = "http://x/${ref.itemId}", isLive = true)
}

/** Points Dispatchers.Main at the test dispatcher for viewModelScope. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
inline fun withMain(dispatcher: TestDispatcher, block: () -> Unit) {
    Dispatchers.setMain(dispatcher)
    try {
        block()
    } finally {
        Dispatchers.resetMain()
    }
}
