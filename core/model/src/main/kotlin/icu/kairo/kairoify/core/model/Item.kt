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
package icu.kairo.kairoify.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Anything Kairoify can list and play. Every item is serializable, so favorites and queues can keep it whole. */
@Serializable
sealed interface Item {
    val ref: MediaRef
    val title: String
    val subtitle: String?
    val artworkUrl: String?
}

@Serializable
@SerialName("station")
data class Station(
    override val ref: MediaRef,
    override val title: String,
    override val subtitle: String? = null,
    override val artworkUrl: String? = null,
    val countryCode: String? = null,
    val tags: List<String> = emptyList(),
    val codec: String? = null,
    val bitrateKbps: Int? = null,
    val homepage: String? = null,
    val streamUrl: String,
    val isHls: Boolean = false,
) : Item

/** One song (or DJ set, or any single recording) from an on-demand source. */
@Serializable
@SerialName("track")
data class Track(
    override val ref: MediaRef,
    override val title: String,
    override val subtitle: String? = null,
    override val artworkUrl: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val durationMillis: Long? = null,
    /** The song's own page on its service ("Open original"). */
    val pageUrl: String? = null,
    /** True when the service streams it as HLS, so the player can pick the right format before resolving. */
    val isHls: Boolean = false,
    /** Creative Commons or public-domain license, shown with the artist (spec §7.7). */
    val license: License? = null,
) : Item

/** A concert, album or record that opens into its tracks. Not playable itself. */
@Serializable
@SerialName("album")
data class Album(
    override val ref: MediaRef,
    override val title: String,
    override val subtitle: String? = null,
    override val artworkUrl: String? = null,
    val artists: List<String> = emptyList(),
    val year: String? = null,
    val trackCount: Int? = null,
    val pageUrl: String? = null,
    val license: License? = null,
) : Item

/** What kind of thing an item is, for filters and search sections. */
enum class ItemKind { STATION, SONG, ALBUM }

val Item.kind: ItemKind
    get() = when (this) {
        is Station -> ItemKind.STATION
        is Track -> ItemKind.SONG
        is Album -> ItemKind.ALBUM
    }

/** Stations and songs can be queued; albums open into their tracks instead. */
val Item.isPlayable: Boolean
    get() = this !is Album
