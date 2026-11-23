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
package icu.kairo.kairoify.core.ytdlp

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionsTest {
    @Test
    fun `orders dated versions`() {
        val ordered = listOf("2026.9.1", "2026.10.1", "2026.10.26", "2026.10.26.1", "2026.11.02")
        assertEquals(ordered, ordered.shuffled(kotlin.random.Random(1)).sortedWith(::compareVersions))
        assertEquals(0, compareVersions("2026.11.02", "2026.11.2"))
    }

    @Test
    fun `bundled version matches the pip pin`() {
        assertTrue(File("requirements.txt").readLines().contains("yt-dlp==$BUNDLED_YTDLP_VERSION"))
    }
}
