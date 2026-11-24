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
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Track
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class SearchOnly : MusicSource {
    override val info = SourceInfo("s", "Sounds")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG
    val queries = mutableListOf<String>()

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        queries += query
        if (query == "broken") throw IOException("down")
        if (query == "empty") return Page(emptyList(), null)
        return Page(listOf(Track(MediaRef("s", query), query)), null)
    }

    override suspend fun resolve(ref: MediaRef) = ResolvedStream(url = "x", isLive = false)
}

class TopicsTest {
    private val inner = SearchOnly()
    private val source = inner.withTopics(listOf(Topic("Rain"), Topic("Birdsong", "birds"), Topic("Broken", "broken"), Topic("Empty", "empty")), shelves = 4)

    @Test
    fun `topics become collections to browse`() = runTest {
        assertTrue(Capability.BROWSE in source.capabilities && Capability.SEARCH in source.capabilities)
        assertEquals(listOf("Rain", "Birdsong", "Broken", "Empty"), source.facets(FacetKind.COLLECTION).map { it.label })
        assertEquals(listOf("birds"), source.browse(BrowseNode.Collection("Birdsong")).items.map { it.title })
        assertTrue(source.browse(BrowseNode.Collection("Nope")).items.isEmpty())
    }

    @Test
    fun `home shows a shelf per topic that found something`() = runTest {
        val shelves = source.home()
        assertEquals(listOf("Sounds · Rain", "Sounds · Birdsong"), shelves.map { it.title })
        assertEquals("s", source.info.id)
    }
}
