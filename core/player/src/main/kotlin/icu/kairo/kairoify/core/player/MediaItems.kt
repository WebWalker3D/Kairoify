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

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemJson
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.Track

/**
 * Converts Kairoify items to Media3 items. The mediaId is always the encoded [MediaRef], and the whole item rides
 * along as JSON in the metadata extras, so any controller can recover exactly what is playing.
 */
object MediaItems {
    const val EXTRA_HOMEPAGE = "kairoify.homepage"
    const val EXTRA_ITEM = "kairoify.item"

    private const val SCHEME = "kairoify"
    private const val AUTHORITY = "resolve"
    private const val PARAM_REF = "ref"

    fun fromItem(item: Item): MediaItem {
        val extras = Bundle().apply { putString(EXTRA_ITEM, ItemJson.encode(item)) }
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.subtitle)
            .setArtworkUri(item.artworkUrl?.toUri())
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .apply {
                when (item) {
                    is Station -> {
                        setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                        setStation(item.title)
                        item.homepage?.let { extras.putString(EXTRA_HOMEPAGE, it) }
                    }
                    is Track -> {
                        setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        setAlbumTitle(item.album)
                        item.durationMillis?.let { setDurationMs(it) }
                    }
                }
            }
            .setExtras(extras)
            .build()
        return MediaItem.Builder().setMediaId(item.ref.encode()).setMediaMetadata(metadata).build()
    }

    /** The Kairoify item a media item was made from, or null when it carries none. */
    fun itemOf(mediaItem: MediaItem): Item? = mediaItem.mediaMetadata.extras?.getString(EXTRA_ITEM)?.let(ItemJson::decode)

    fun refOf(item: MediaItem): MediaRef = MediaRef.decode(item.mediaId)

    /** A URI that stands in for the stream until the player needs it; see [resolveDataSpec]. */
    fun placeholderUri(ref: MediaRef): Uri =
        Uri.Builder().scheme(SCHEME).authority(AUTHORITY).appendQueryParameter(PARAM_REF, ref.encode()).build()

    fun refOfPlaceholder(uri: Uri): MediaRef? {
        if (uri.scheme != SCHEME || uri.authority != AUTHORITY) return null
        val encoded = uri.getQueryParameter(PARAM_REF) ?: return null
        return runCatching { MediaRef.decode(encoded) }.getOrNull()
    }

    /**
     * Readies an item for the player without contacting any source: the stream is resolved later, when audio is
     * first needed. HLS has to be chosen now, so items that stream as HLS say so up front.
     */
    fun forPlayer(mediaItem: MediaItem): MediaItem {
        val hls = when (val item = itemOf(mediaItem)) {
            is Station -> item.isHls
            is Track -> item.isHls
            null -> false
        }
        return mediaItem.buildUpon()
            .setUri(placeholderUri(refOf(mediaItem)))
            .setMimeType(if (hls) MimeTypes.APPLICATION_M3U8 else null)
            .build()
    }
}
