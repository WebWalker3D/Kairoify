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

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.StreamMime

/** Converts Kairoify items to Media3 items. The mediaId is always the encoded [MediaRef]. */
object MediaItems {
    const val EXTRA_HOMEPAGE = "kairoify.homepage"

    fun fromItem(item: Item): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.subtitle)
            .setArtworkUri(item.artworkUrl?.toUri())
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .apply {
                if (item is Station) {
                    setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    setStation(item.title)
                    item.homepage?.let { setExtras(Bundle().apply { putString(EXTRA_HOMEPAGE, it) }) }
                }
            }
            .build()
        return MediaItem.Builder().setMediaId(item.ref.encode()).setMediaMetadata(metadata).build()
    }

    fun withStream(item: MediaItem, stream: ResolvedStream): MediaItem =
        item.buildUpon()
            .setUri(stream.url)
            .setMimeType(if (stream.mimeType == StreamMime.HLS) MimeTypes.APPLICATION_M3U8 else stream.mimeType)
            .build()

    fun refOf(item: MediaItem): MediaRef = MediaRef.decode(item.mediaId)
}
