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
package icu.kairo.kairoify.source.nightride

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo

/** Nightride FM: free, listener-supported synthwave radio. Its stations are a fixed list, so nothing is fetched until play. */
class NightrideSource : MusicSource {
    override val info = SourceInfo(ID, "Nightride FM")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH, Capability.LIVE)
    override val searchKind = ItemKind.STATION

    override suspend fun home(): List<Shelf> = listOf(Shelf("nightride:all", "Nightride FM", CHANNELS.map(::station)))

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.isEmpty()) return Page(emptyList(), next = null)
        val hits = CHANNELS.filter { c ->
            val text = "${c.title} ${c.about} synthwave nightride".lowercase()
            words.all { it in text }
        }
        return Page(hits.map(::station), next = null)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        if (CHANNELS.none { it.id == ref.itemId }) throw SourceException.NotFound("No Nightride station '${ref.itemId}'")
        return ResolvedStream(url = streamUrl(ref.itemId), isLive = true)
    }

    private fun station(c: Channel) = Station(
        ref = MediaRef(ID, c.id),
        title = c.title,
        subtitle = "Nightride FM · ${c.about}",
        tags = c.about.split(", "),
        artworkUrl = null,
        homepage = "https://nightride.fm/",
        streamUrl = streamUrl(c.id),
    )

    internal data class Channel(val id: String, val title: String, val about: String)

    companion object {
        const val ID = "nightride"

        fun streamUrl(id: String) = "https://stream.nightride.fm/$id.mp3"

        internal val CHANNELS = listOf(
            Channel("nightride", "Nightride FM", "synthwave, retrowave, outrun"),
            Channel("chillsynth", "Chillsynth", "chillwave, lo-fi synth, dreamwave"),
            Channel("datawave", "Datawave", "chiptune, glitch, cyber electronica"),
            Channel("spacesynth", "Spacesynth", "space disco, italo, cosmic synth"),
            Channel("darksynth", "Darksynth", "darkwave, cyberpunk, industrial synth"),
            Channel("horrorsynth", "Horrorsynth", "horror synth, dark ambient"),
            Channel("ebsm", "EBSM", "electronic body music, EBM, industrial"),
            Channel("rekt", "Rekt", "drum and bass, dubstep, bass music"),
            Channel("rektory", "Rektory", "techno, hard dance, rave"),
        )
    }
}
