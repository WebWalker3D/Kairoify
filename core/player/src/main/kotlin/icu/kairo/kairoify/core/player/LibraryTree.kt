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
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.isPlayable
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.source.SourceResult
import kotlinx.coroutines.flow.toList
import java.util.concurrent.ConcurrentHashMap

/**
 * Browse tree for Android Auto and other media browsers: root → Favorites, then one folder per Home shelf → playable
 * items. Playing a favorite queues the whole Favorites folder, so the car's ⏭/⏮ step through them like presets.
 */
class LibraryTree(private val registry: SourceRegistry, private val favorites: suspend () -> List<Item>) {
    private val known = ConcurrentHashMap<String, MediaItem>()
    private val lastFolder = ConcurrentHashMap<String, String>()

    /** Folders whose contents play as one queue (Favorites, and each opened album or concert), by folder id. */
    private val queueFolders = ConcurrentHashMap<String, List<String>>()

    @Volatile private var shelves: List<Shelf> = emptyList()

    suspend fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT_ID -> listOf(folder(FAVORITES_ID, "Favorites")) + loadShelves().map { folder(SHELF_PREFIX + it.id, it.title) }
        // Only playable favorites: albums would be dead entries in a car's preset queue.
        parentId == FAVORITES_ID -> listed(FAVORITES_ID, favorites().filter { it.isPlayable }).also { list -> queueFolders[FAVORITES_ID] = list.map { it.mediaId } }
        parentId.startsWith(SHELF_PREFIX) -> {
            val shelfId = parentId.removePrefix(SHELF_PREFIX)
            val shelf = shelves.firstOrNull { it.id == shelfId } ?: loadShelves().firstOrNull { it.id == shelfId }
            listed(parentId, shelf?.items.orEmpty())
        }
        // An album or concert the browser saw: open it into its tracks, which then play as one queue.
        known[parentId]?.mediaMetadata?.mediaType == MediaMetadata.MEDIA_TYPE_ALBUM -> {
            val tracks = runCatching { registry.details(MediaRef.decode(parentId)).tracks }.getOrDefault(emptyList())
            listed(parentId, tracks).also { list -> queueFolders[parentId] = list.map { it.mediaId } }
        }
        else -> emptyList()
    }

    /** Full metadata for an id a browser already saw; browsers often send back only the id. */
    fun item(mediaId: String): MediaItem? = if (mediaId == ROOT_ID) root() else known[mediaId]

    /**
     * When [mediaId] was last seen in Favorites or inside an album: that folder as a queue, and where the item sits in
     * it — so a car's ⏭/⏮ step through favorites like presets, or through a concert song by song.
     */
    fun queueFor(mediaId: String): Pair<List<MediaItem>, Int>? {
        val ids = lastFolder[mediaId]?.let { queueFolders[it] } ?: return null
        val index = ids.indexOf(mediaId).takeIf { it >= 0 } ?: return null
        return ids.mapNotNull { known[it] } to index
    }

    private fun listed(folderId: String, items: List<Item>): List<MediaItem> = items.map { item ->
        MediaItems.fromItem(item).also {
            known[it.mediaId] = it
            lastFolder[it.mediaId] = folderId
        }
    }

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
        const val FAVORITES_ID = "favorites"
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
