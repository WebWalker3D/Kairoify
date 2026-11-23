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
package icu.kairo.kairoify.source.radiobrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationMapperTest {
    private val base = StationDto(stationuuid = "u1", name = "Jazz FM", url = "http://a/stream", urlResolved = "http://b/stream")

    @Test
    fun `blank name becomes Unnamed station`() {
        assertEquals("Unnamed station", base.copy(name = "   ").toStation().title)
    }

    @Test
    fun `non-http favicon is dropped`() {
        assertNull(base.copy(favicon = "data:image/png;base64,xx").toStation().artworkUrl)
        assertEquals("https://x/f.png", base.copy(favicon = "https://x/f.png").toStation().artworkUrl)
    }

    @Test
    fun `tags are split and trimmed`() {
        assertEquals(listOf("jazz", "smooth jazz"), base.copy(tags = " jazz, smooth jazz ,,").toStation().tags)
    }

    @Test
    fun `url_resolved preferred, url fallback`() {
        assertEquals("http://b/stream", base.toStation().streamUrl)
        assertEquals("http://a/stream", base.copy(urlResolved = "").toStation().streamUrl)
    }

    @Test
    fun `hls flag maps to isHls`() {
        assertTrue(base.copy(hls = 1).toStation().isHls)
        assertFalse(base.toStation().isHls)
    }
}
