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
package icu.kairo.kairoify.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.source.SourceResult
import kotlinx.coroutines.flow.toList
import java.util.concurrent.ConcurrentHashMap

/** Browse tree for Android Auto and other media browsers: root → one folder per Home shelf → playable items. */
class LibraryTree(private val registry: SourceRegistry) {
    private val known = ConcurrentHashMap<String, MediaItem>()

    @Volatile private var shelves: List<Shelf> = emptyList()

    suspend fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT_ID -> loadShelves().map { folder(SHELF_PREFIX + it.id, it.title) }
        parentId.startsWith(SHELF_PREFIX) -> {
            val shelfId = parentId.removePrefix(SHELF_PREFIX)
            val shelf = shelves.firstOrNull { it.id == shelfId } ?: loadShelves().firstOrNull { it.id == shelfId }
            shelf?.items.orEmpty().map { MediaItems.fromItem(it).also { item -> known[item.mediaId] = item } }
        }
        else -> emptyList()
    }

    /** Full metadata for an id a browser already saw; browsers often send back only the id. */
    fun item(mediaId: String): MediaItem? = if (mediaId == ROOT_ID) root() else known[mediaId]

    private suspend fun loadShelves(): List<Shelf> =
        registry.home().toList()
            .flatMap { result ->
                when (result) {
                    is SourceResult.Ok -> result.value
                    is SourceResult.Failed -> emptyList()
                }
            }
            .also { shelves = it }

    companion object {
        const val ROOT_ID = "root"
        private const val SHELF_PREFIX = "home:"

        fun root(): MediaItem = folder(ROOT_ID, "Kairoify")

        private fun folder(id: String, title: String): MediaItem =
            MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build(),
                )
                .build()
    }
}
