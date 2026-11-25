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
package icu.kairo.kairoify.ui.home

import icu.kairo.kairoify.core.data.HistoryStore
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.mainArtist
import icu.kairo.kairoify.core.source.normalizeName
import kotlinx.coroutines.flow.first

/**
 * Home's own rows, built on the phone from History: what was played lately, and music like the artist played most.
 * [similarTo] is Free radio's station builder; nothing about the person leaves the phone except that artist's name.
 */
class PersonalShelves(private val history: HistoryStore, private val similarTo: suspend (Item) -> List<Item>) {
    suspend fun shelves(): List<Shelf> {
        val recent = history.recent(RECENT).first().map { it.item }
        if (recent.isEmpty()) return emptyList()
        val shelves = mutableListOf(Shelf("you:recent", "Recently played", recent))
        val seed = history.mostPlayed(TOP).map { it.item }.filterIsInstance<Track>().firstOrNull { mainArtist(it) != null }
        if (seed != null) {
            val artist = mainArtist(seed)!!
            val played = recent.map { it.ref }.toSet()
            val picks = similarTo(seed).filter { it.ref !in played && mainArtist(it)?.let(::normalizeName) != normalizeName(artist) }.take(SIMILAR)
            if (picks.isNotEmpty()) shelves += Shelf("you:because", "Because you play $artist", picks)
        }
        return shelves
    }

    private companion object {
        const val RECENT = 20
        const val TOP = 10
        const val SIMILAR = 20
    }
}
