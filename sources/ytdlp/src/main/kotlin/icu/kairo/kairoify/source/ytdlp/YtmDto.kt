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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val lenientJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class YtmArtist(val name: String? = null)

@Serializable
internal data class YtmAlbum(val name: String? = null)

@Serializable
internal data class YtmThumb(val url: String? = null, val width: Int? = null)

/** One ytmusicapi search result (filter="songs"). */
@Serializable
internal data class YtmSong(
    val videoId: String? = null,
    val title: String? = null,
    val resultType: String? = null,
    val artists: List<YtmArtist> = emptyList(),
    val album: YtmAlbum? = null,
    @SerialName("duration_seconds") val durationSeconds: Long? = null,
    val thumbnails: List<YtmThumb> = emptyList(),
)

/** One track of ytmusicapi's watch playlist ("radio"). */
@Serializable
internal data class YtmWatchTrack(
    val videoId: String? = null,
    val title: String? = null,
    val artists: List<YtmArtist> = emptyList(),
    val album: YtmAlbum? = null,
    val length: String? = null,
    val thumbnail: List<YtmThumb> = emptyList(),
)
