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
package icu.kairo.kairoify.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import icu.kairo.kairoify.core.model.Album
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
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.AlbumDetails
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The music files on this phone, from Android's media library. Needs the person's OK to read audio. */
class LocalMusicSource(context: Context) : MusicSource {
    private val context = context.applicationContext
    override val info = SourceInfo(ID, "On this phone")
    override val capabilities = setOf(Capability.SEARCH, Capability.HOME, Capability.BROWSE, Capability.DETAILS)
    override val searchKind = ItemKind.SONG

    /** Whether the person has let Kairoify read audio files. */
    fun hasAccess(): Boolean = ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val q = query.trim()
        if (q.isEmpty() || !hasAccess()) return Page.empty()
        val like = "%" + q.replace("%", "").replace("_", "") + "%"
        val where = "$MUSIC AND (${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ? OR ${MediaStore.Audio.Media.ALBUM} LIKE ?)"
        return Page(songs(where, arrayOf(like, like, like), MediaStore.Audio.Media.TITLE, 100), next = null)
    }

    override suspend fun home(): List<Shelf> {
        if (!hasAccess()) return emptyList()
        val recent = songs(MUSIC, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC", 30)
        val albums = albums(60)
        return listOfNotNull(
            recent.takeIf { it.isNotEmpty() }?.let { Shelf("local:recent", "On this phone · Recently added", it) },
            albums.takeIf { it.isNotEmpty() }?.let { Shelf("local:albums", "On this phone · Albums", it) },
        )
    }

    override suspend fun facets(kind: FacetKind): List<Facet> =
        if (kind == FacetKind.COLLECTION && hasAccess()) listOf(Facet(BrowseNode.Collection(ALL), "All songs on this phone", null)) else emptyList()

    override suspend fun browse(node: BrowseNode, page: PageToken?): Page<Item> =
        if ((node as? BrowseNode.Collection)?.id == ALL) Page(allSongs(), next = null) else Page.empty()

    /** Every song, A to Z. */
    suspend fun allSongs(): List<Track> = if (hasAccess()) songs(MUSIC, null, MediaStore.Audio.Media.TITLE, 5_000) else emptyList()

    override suspend fun details(ref: MediaRef): AlbumDetails {
        val albumId = ref.itemId.removePrefix(ALBUM_PREFIX).toLongOrNull() ?: throw SourceException.NotFound("No such album")
        val album = albums(1, "${MediaStore.Audio.Albums._ID} = ?", arrayOf(albumId.toString())).firstOrNull()
            ?: throw SourceException.NotFound("That album isn't on this phone any more.")
        val tracks = songs("$MUSIC AND ${MediaStore.Audio.Media.ALBUM_ID} = ?", arrayOf(albumId.toString()), "${MediaStore.Audio.Media.TRACK}, ${MediaStore.Audio.Media.TITLE}", 500)
        return AlbumDetails(album, tracks)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val id = ref.itemId.toLongOrNull() ?: throw SourceException.NotFound("No such file")
        return ResolvedStream(url = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(), isLive = false)
    }

    private suspend fun songs(where: String, args: Array<String>?, order: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val columns = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
        )
        val out = mutableListOf<Track>()
        try {
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns, where, args, order)?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val artist = c.getString(2)?.takeUnless { it == UNKNOWN }
                    out += Track(
                        ref = MediaRef(ID, c.getLong(0).toString()),
                        title = c.getString(1) ?: continue,
                        subtitle = artist,
                        artworkUrl = albumArt(c.getLong(4)),
                        artists = listOfNotNull(artist),
                        album = c.getString(3),
                        durationMillis = c.getLong(5).takeIf { it > 0 },
                    )
                }
            }
        } catch (e: SecurityException) {
            return@withContext emptyList()
        }
        out
    }

    private suspend fun albums(limit: Int, where: String? = null, args: Array<String>? = null): List<Album> = withContext(Dispatchers.IO) {
        val columns = arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.NUMBER_OF_SONGS)
        val out = mutableListOf<Album>()
        try {
            context.contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, columns, where, args, MediaStore.Audio.Albums.ALBUM)?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val artist = c.getString(2)?.takeUnless { it == UNKNOWN }
                    out += Album(
                        ref = MediaRef(ID, ALBUM_PREFIX + c.getLong(0)),
                        title = c.getString(1) ?: continue,
                        subtitle = artist,
                        artworkUrl = albumArt(c.getLong(0)),
                        artists = listOfNotNull(artist),
                        trackCount = c.getInt(3),
                    )
                }
            }
        } catch (e: SecurityException) {
            return@withContext emptyList()
        }
        out
    }

    private fun albumArt(albumId: Long): String = ContentUris.withAppendedId(ALBUM_ART, albumId).toString()

    companion object {
        const val ID = "local"
        private const val ALL = "all"
        private const val ALBUM_PREFIX = "album:"
        private const val UNKNOWN = "<unknown>"
        private val MUSIC = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")

        /** The permission that lets Kairoify read music files. */
        val PERMISSION: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
