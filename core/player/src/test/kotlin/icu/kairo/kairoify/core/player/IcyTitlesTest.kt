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

import androidx.media3.common.MediaMetadata
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class IcyTitlesTest {
    private val metadata = MediaMetadata.Builder().setTitle("Jazz FM").setArtist("Germany · jazz").build()

    @Test
    fun `blank icy title is ignored`() {
        assertNull(withIcyTitle(metadata, "   "))
        assertNull(withIcyTitle(metadata, null))
    }

    @Test
    fun `same title is ignored`() {
        val current = metadata.buildUpon().setArtist("Miles Davis - So What").build()
        assertNull(withIcyTitle(current, " Miles Davis - So What "))
    }

    @Test
    fun `new title replaces artist and keeps title`() {
        val updated = assertNotNull(withIcyTitle(metadata, " Miles Davis - So What "))
        assertEquals("Miles Davis - So What", updated.artist.toString())
        assertEquals("Jazz FM", updated.title.toString())
    }
}
