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
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.Shelf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A topic to browse: what the person sees, and the search that fills it. */
data class Topic(val label: String, val query: String = label.lowercase())

/**
 * Gives a search-only source things to browse: each [topics] entry becomes a collection that runs its search, and the
 * first [shelves] of them become shelves on its section's page.
 */
class WithTopics(private val inner: MusicSource, private val topics: List<Topic>, private val shelves: Int = 3) : MusicSource by inner {
    override val capabilities: Set<Capability> = inner.capabilities + Capability.BROWSE + Capability.HOME

    override suspend fun facets(kind: FacetKind): List<Facet> =
        if (kind == FacetKind.COLLECTION) topics.map { Facet(BrowseNode.Collection(it.label), it.label, null) } else inner.facets(kind)

    override suspend fun browse(node: BrowseNode, page: PageToken?): Page<Item> {
        val topic = (node as? BrowseNode.Collection)?.let { c -> topics.firstOrNull { it.label == c.id } } ?: return inner.browse(node, page)
        return inner.search(topic.query, page)
    }

    override suspend fun home(): List<Shelf> = coroutineScope {
        val own = async { runCatchingSource { inner.home() }.orEmpty() }
        val picks = topics.take(shelves).map { topic ->
            async {
                runCatchingSource { inner.search(topic.query).items }?.takeIf { it.isNotEmpty() }
                    ?.let { Shelf("${inner.info.id}:topic:${topic.label}", "${inner.info.displayName} · ${topic.label}", it) }
            }
        }
        own.await() + picks.awaitAll().filterNotNull()
    }

    private suspend fun <T> runCatchingSource(call: suspend () -> T): T? = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/** [WithTopics] as an extension, for wiring sources up in one line. */
fun MusicSource.withTopics(topics: List<Topic>, shelves: Int = 3): MusicSource = WithTopics(this, topics, shelves)
