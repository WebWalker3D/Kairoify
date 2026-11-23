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
package icu.kairo.kairoify.source.ytdlp

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceInfo
import icu.kairo.kairoify.core.ytdlp.MusicEngine
import icu.kairo.kairoify.core.ytdlp.ResolveMode

/** Independent artists on Bandcamp: searched through Bandcamp's own search, played through yt-dlp. */
class BandcampSource(private val api: BandcampSearchApi, private val engine: MusicEngine) : MusicSource {
    override val info = SourceInfo(ID, "Bandcamp")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG
    override val searchTimeoutMillis = 10_000L

    override suspend fun search(query: String, page: PageToken?): Page<Item> =
        Page(api.searchTracks(query).mapNotNull(::track), next = null)

    /** The item id is the track's page URL; yt-dlp reads the stream from it. */
    override suspend fun resolve(ref: MediaRef): ResolvedStream = engine.resolve(ref.itemId, ResolveMode.FAST).toResolvedStream()

    private fun track(result: BcResult): Track? {
        val page = result.itemUrlPath?.takeIf { it.startsWith("https://") && "/track/" in it } ?: return null
        val title = result.name?.takeIf(String::isNotBlank) ?: return null
        val band = result.bandName?.takeIf(String::isNotBlank)
        return Track(
            ref = MediaRef(ID, page),
            title = title,
            subtitle = band,
            // Size "_2" is 350 px: enough for rows and Now Playing.
            artworkUrl = result.artId?.let { "https://f4.bcbits.com/img/a${it}_2.jpg" },
            artists = listOfNotNull(band),
            album = result.albumName,
            pageUrl = page,
        )
    }

    companion object {
        const val ID = "bandcamp"
    }
}
