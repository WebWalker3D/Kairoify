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
package icu.kairo.kairoify.core.transcribe

import icu.kairo.kairoify.source.lyrics.LyricLine
import org.junit.Test
import kotlin.test.assertEquals

class AudioTest {
    @Test
    fun `stereo is averaged and 32 kHz halves to 16 kHz`() {
        val stereo = floatArrayOf(1f, 0f, 0.5f, 0.5f, 0f, 0f, -1f, -1f)
        val mono = toMono16k(stereo, channels = 2, sampleRate = 32_000)
        assertEquals(listOf(0.5f, 0f), mono.toList())
    }

    @Test
    fun `16 kHz mono passes through and 16-bit samples scale to -1 to 1`() {
        assertEquals(listOf(0.25f, -0.25f), toMono16k(floatArrayOf(0.25f, -0.25f), 1, 16_000).toList())
        assertEquals(listOf(0.5f, -1f), pcm16ToFloat(shortArrayOf(16384, -32768)).toList())
    }

    @Test
    fun `segments shift by where the chunk starts and blanks are dropped`() {
        val raw = arrayOf("0\t2500\t Four score".toByteArray(), "2500\t4000\t  ".toByteArray(), "4000\t6000\t[MUSIC]".toByteArray(), "6000\t9000\t and seven".toByteArray())
        assertEquals(listOf(LyricLine(60_000, "Four score"), LyricLine(66_000, "and seven")), parseSegments(raw, offsetMillis = 60_000))
    }
}
