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
package icu.kairo.kairoify.core.source

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongMatchingTest {
    private fun t(src: String, id: String, title: String, artist: String, secs: Long? = null) =
        Track(MediaRef(src, id), title, subtitle = artist, artists = listOf(artist), durationMillis = secs?.times(1000))

    private val wanted = t("youtube", "y1", "Feeling Good", "Nina Simone", 177)

    @Test
    fun `finds the same song elsewhere and skips remixes and other artists`() {
        val pick = bestMatch(
            wanted,
            listOf(
                t("audius", "a1", "Feeling Good (Soul Flip Edit)", "Nina Simone", 180),
                t("audius", "a2", "Feeling Good", "Nina Simone Tribute", 177),
                t("archive", "r1", "Feeling Good (Remastered)", "Nina Simone", 178),
            ),
        )
        assertEquals("r1", pick?.ref?.itemId)
    }

    @Test
    fun `prefers the closest length and refuses a very different one`() {
        val far = t("archive", "far", "Feeling Good", "Nina Simone", 400)
        val near = t("bandcamp", "near", "Feeling Good", "Nina Simone", 175)
        assertEquals("near", bestMatch(wanted, listOf(far, near))?.ref?.itemId)
        assertNull(bestMatch(wanted, listOf(far)))
    }

    @Test
    fun `video titles with the artist in front still match`() {
        val yt = t("soundcloud", "s", "Nina Simone - Feeling Good", "Nina Simone", 177)
        assertEquals("s", bestMatch(t("audius", "x", "Feeling Good", "Nina Simone", 177), listOf(yt))?.ref?.itemId)
    }

    @Test
    fun `a wanted remix may match a remix`() {
        val w = t("youtube", "y", "Feeling Good (Live)", "Nina Simone")
        assertEquals("l", bestMatch(w, listOf(t("archive", "l", "Feeling Good (Live at Montreux)", "Nina Simone")))?.ref?.itemId)
    }
}
