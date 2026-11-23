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

import icu.kairo.kairoify.core.model.Album
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.AlbumDetails
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceInfo
import icu.kairo.kairoify.core.source.SourceRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LibraryTreeTest {
    private val shelfStation = Station(MediaRef("radiobrowser", "s1"), "Shelf FM", streamUrl = "http://x/1")

    private val source = object : MusicSource {
        override val info = SourceInfo("radiobrowser", "Radio Browser")
        override val capabilities = setOf(Capability.HOME)
        override val searchKind = ItemKind.STATION
        override suspend fun home() = listOf(Shelf("top", "Most popular", listOf(shelfStation)))
        override suspend fun resolve(ref: MediaRef) = ResolvedStream("http://x", isLive = true)
    }

    private val favorites: List<Item> = listOf(
        Station(MediaRef("radiobrowser", "f1"), "Fav One", streamUrl = "http://x/f1"),
        Track(MediaRef("youtube", "v2"), "Fav Two"),
        Station(MediaRef("radiobrowser", "f3"), "Fav Three", streamUrl = "http://x/f3"),
    )

    private val tree = LibraryTree(SourceRegistry(listOf(source))) { favorites }

    @Test
    fun `root lists Favorites before home shelves`() = runTest {
        val titles = tree.children(LibraryTree.ROOT_ID).map { it.mediaMetadata.title.toString() }
        assertEquals(listOf("Favorites", "Most popular"), titles)
    }

    @Test
    fun `favorites folder lists playable favorite items`() = runTest {
        val items = tree.children(LibraryTree.FAVORITES_ID)
        assertEquals(favorites.map { it.ref.encode() }, items.map { it.mediaId })
        assertTrue(items.all { it.mediaMetadata.isPlayable == true })
    }

    @Test
    fun `items from favorites are known by id`() = runTest {
        tree.children(LibraryTree.FAVORITES_ID)
        assertEquals("Fav Two", assertNotNull(tree.item("youtube:v2")).mediaMetadata.title.toString())
    }

    @Test
    fun `queueFor a favorite returns the whole folder from it`() = runTest {
        tree.children(LibraryTree.FAVORITES_ID)
        val (items, index) = assertNotNull(tree.queueFor("youtube:v2"))
        assertEquals(favorites.map { it.ref.encode() }, items.map { it.mediaId })
        assertEquals(1, index)
    }

    @Test
    fun `queueFor an item outside favorites is null`() = runTest {
        tree.children(LibraryTree.FAVORITES_ID)
        tree.children(LibraryTree.ROOT_ID).last().let { tree.children(it.mediaId) }
        assertNull(tree.queueFor(shelfStation.ref.encode()))
    }

    @Test
    fun `albums stay out of the favorites folder`() = runTest {
        val withAlbum = LibraryTree(SourceRegistry(listOf(source))) { favorites + Album(MediaRef("archive", "show"), "A show") }
        val ids = withAlbum.children(LibraryTree.FAVORITES_ID).map { it.mediaId }
        assertEquals(favorites.map { it.ref.encode() }, ids)
    }

    private val concert = Album(MediaRef("archive", "gd77"), "Barton Hall")
    private val concertTracks = listOf(Track(MediaRef("archive", "gd77/t1.mp3"), "One"), Track(MediaRef("archive", "gd77/t2.mp3"), "Two"))
    private val archive = object : MusicSource {
        override val info = SourceInfo("archive", "Internet Archive")
        override val capabilities = setOf(Capability.HOME, Capability.DETAILS)
        override val searchKind = ItemKind.ALBUM
        override suspend fun home() = listOf(Shelf("archive:onthisday", "On this day", listOf(concert)))
        override suspend fun details(ref: MediaRef) = AlbumDetails(concert, concertTracks)
        override suspend fun resolve(ref: MediaRef) = ResolvedStream("http://x", isLive = false)
    }

    @Test
    fun `concerts open into their tracks`() = runTest {
        val tree = LibraryTree(SourceRegistry(listOf(archive))) { emptyList() }
        val shelf = tree.children(LibraryTree.ROOT_ID).last()
        val folder = tree.children(shelf.mediaId).single()
        assertEquals(true, folder.mediaMetadata.isBrowsable)
        assertEquals(listOf("archive:gd77/t1.mp3", "archive:gd77/t2.mp3"), tree.children(folder.mediaId).map { it.mediaId })
    }

    @Test
    fun `playing a concert track queues the whole concert`() = runTest {
        val tree = LibraryTree(SourceRegistry(listOf(archive))) { emptyList() }
        tree.children(tree.children(tree.children(LibraryTree.ROOT_ID).last().mediaId).single().mediaId)
        val (items, index) = assertNotNull(tree.queueFor("archive:gd77/t2.mp3"))
        assertEquals(2, items.size)
        assertEquals(1, index)
    }
}
