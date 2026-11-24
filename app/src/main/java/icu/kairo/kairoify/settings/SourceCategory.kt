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
package icu.kairo.kairoify.settings

/** The sections Kairoify's sources are grouped into; each has its own page with its own search. */
enum class SourceCategory(val label: String, val description: String, val searchHint: String) {
    MUSIC("Music", "Songs, albums, live concerts, mixes and mixtapes", "Songs, artists, albums…"),
    RADIO("Radio", "Live stations from every country, plus ad-free channels", "Stations, genres, countries…"),
    AUDIOBOOKS("Audiobooks & poetry", "Public-domain books and poems, read aloud", "Books, authors, poems…"),
    PODCASTS("Podcasts", "Every show in Apple's public directory", "Shows and topics…"),
    HISTORY("Historical recordings", "Early recordings, 78s and archives", "Recordings, performers, years…"),
    SOUNDS("Sounds", "Creative Commons audio, field recordings and ambience", "Sounds and textures…"),
}
