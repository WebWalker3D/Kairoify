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

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.source.SourceException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
class ResolvingStreamsTest {
    private val ref = MediaRef("youtube", "abc")

    @Test
    fun `placeholder becomes the resolved url with headers added`() {
        val spec = DataSpec.Builder()
            .setUri(MediaItems.placeholderUri(ref))
            .setHttpRequestHeaders(mapOf("Icy-MetaData" to "1"))
            .build()
        val resolved = resolveDataSpec(spec) {
            assertEquals(ref, it)
            ResolvedStream("https://cdn.example/a.m4a", headers = mapOf("User-Agent" to "X"), isLive = false)
        }
        assertEquals("https://cdn.example/a.m4a", resolved.uri.toString())
        assertEquals(mapOf("Icy-MetaData" to "1", "User-Agent" to "X"), resolved.httpRequestHeaders)
    }

    @Test
    fun `other uris pass through untouched`() {
        val spec = DataSpec(Uri.parse("https://cdn.example/seg1.ts"))
        assertSame(spec, resolveDataSpec(spec) { error("must not resolve") })
    }

    @Test
    fun `source failures become ResolveException with the reason`() {
        val spec = DataSpec(MediaItems.placeholderUri(ref))
        val e = assertFailsWith<ResolveException> { resolveDataSpec(spec) { throw SourceException.NotFound("gone") } }
        assertIs<SourceException.NotFound>(e.reason)
        assertEquals("gone", e.message)
    }

    @Test
    fun `unexpected failures become Unavailable`() {
        val spec = DataSpec(MediaItems.placeholderUri(ref))
        val e = assertFailsWith<ResolveException> { resolveDataSpec(spec) { throw IOException("socket closed") } }
        assertIs<SourceException.Unavailable>(e.reason)
    }

    @Test
    fun `resolve failures are not retried`() {
        val error = ResolveException(SourceException.NotFound("gone"))
        assertEquals(C.TIME_UNSET, ResolveAwareLoadErrorPolicy().getRetryDelayMsFor(infoFor(IOException("wrapped", error))))
    }

    @Test
    fun `other load errors still retry`() {
        assertNotEquals(C.TIME_UNSET, ResolveAwareLoadErrorPolicy().getRetryDelayMsFor(infoFor(IOException("x"))))
    }

    private fun infoFor(e: IOException) = LoadErrorHandlingPolicy.LoadErrorInfo(
        LoadEventInfo(1, DataSpec(Uri.parse("https://x")), 0),
        MediaLoadData(C.DATA_TYPE_MEDIA),
        e,
        2,
    )
}
