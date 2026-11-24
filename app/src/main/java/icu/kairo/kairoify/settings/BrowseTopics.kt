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

import icu.kairo.kairoify.core.source.Topic

/** What the search-only sources offer to browse. The first few of each list become shelves on the section's page. */
object BrowseTopics {
    val podcasts = listOf(
        Topic("History"), Topic("True crime"), Topic("Science"), Topic("Comedy"), Topic("News"), Topic("Technology"),
        Topic("Music"), Topic("Audio drama"), Topic("Books"), Topic("Business"), Topic("Health"), Topic("Sports"),
        Topic("Kids & family", "kids"), Topic("Philosophy"), Topic("Nature"), Topic("Art"), Topic("Film"), Topic("Language learning"),
    )

    val libraryOfCongress = listOf(
        Topic("National Jukebox", "victor talking machine"), Topic("Speeches", "speech"), Topic("Folk songs", "folk song"),
        Topic("Oral histories", "oral history"), Topic("Blues"), Topic("Jazz"), Topic("Opera"), Topic("Poetry readings", "poetry reading"),
        Topic("Cowboy songs", "cowboy songs"), Topic("Fiddle tunes", "fiddle"), Topic("Spirituals", "spiritual"), Topic("World War I", "world war"),
    )

    val wikimedia = listOf(
        Topic("Historical recordings", "historical recording"), Topic("Speeches", "speech"), Topic("Birdsong", "bird song"),
        Topic("National anthems", "national anthem"), Topic("Classical", "symphony"), Topic("Folk songs", "folk song"),
        Topic("Wax cylinders", "wax cylinder"), Topic("Piano"),
    )

    val europeana = listOf(
        Topic("Folk music", "folk music"), Topic("Speeches", "speech"), Topic("Opera"), Topic("Jazz"), Topic("Interviews", "interview"),
        Topic("Gramophone records", "gramophone"), Topic("Radio broadcasts", "radio broadcast"),
    )

    val sounds = listOf(
        Topic("Rain"), Topic("Birds"), Topic("Ocean", "ocean waves"), Topic("Thunder"), Topic("Forest"), Topic("City", "city ambience"),
        Topic("Wind"), Topic("Fire", "fireplace"), Topic("Footsteps"), Topic("Bells"), Topic("Trains", "train"), Topic("Crowds", "crowd"),
        Topic("Water", "stream water"), Topic("Night", "night crickets"),
    )
}
