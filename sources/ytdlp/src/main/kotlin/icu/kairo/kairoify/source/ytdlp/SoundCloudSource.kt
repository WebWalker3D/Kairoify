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
import kotlinx.serialization.json.decodeFromJsonElement

/** Indie tracks, DJ sets and remixes from SoundCloud, through yt-dlp. SoundCloud streams are HLS. */
class SoundCloudSource(private val engine: MusicEngine) : MusicSource {
    override val info = SourceInfo(ID, "SoundCloud")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG
    override val searchTimeoutMillis = 15_000L

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val tracks = engine.flatSearch("scsearch$PAGE_SIZE:$query")
            .mapNotNull { runCatching { lenientJson.decodeFromJsonElement<ScEntry>(it) }.getOrNull() }
            .mapNotNull(::track)
        return Page(tracks, next = null)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream =
        engine.resolve(SITE + ref.itemId, ResolveMode.FAST).toResolvedStream()

    private fun track(entry: ScEntry): Track? {
        val page = entry.webpageUrl?.takeIf { it.startsWith(SITE) } ?: return null
        val title = entry.title?.takeIf(String::isNotBlank) ?: return null
        // Go+ tracks come back as 30-second previews; they'd cut off mid-song, so they're left out.
        if (entry.duration != null && entry.duration in PREVIEW_SECONDS) return null
        val artists = entry.artists?.filter(String::isNotBlank)?.takeIf { it.isNotEmpty() } ?: listOfNotNull(entry.uploader)
        return Track(
            ref = MediaRef(ID, page.removePrefix(SITE)),
            title = title,
            subtitle = entry.uploader,
            artworkUrl = (entry.thumbnails.firstOrNull { it.id == "t500x500" } ?: entry.thumbnails.maxByOrNull { it.width ?: 0 })?.url,
            artists = artists,
            durationMillis = entry.duration?.let { (it * 1000).toLong() },
            pageUrl = page,
            isHls = true,
        )
    }

    companion object {
        const val ID = "soundcloud"
        private const val SITE = "https://soundcloud.com/"
        private const val PAGE_SIZE = 20
        private val PREVIEW_SECONDS = 29.0..31.0
    }
}
