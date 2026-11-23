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

import androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
import icu.kairo.kairoify.core.source.SourceException
import org.junit.Test
import kotlin.test.assertEquals

class TrackRecoveryTest {
    private val gone = SourceException.NotFound("gone")

    @Test
    fun `expired url is refreshed once`() =
        assertEquals(Recovery.REFRESH_URL, trackRecovery(ERROR_CODE_IO_BAD_HTTP_STATUS, null, refreshedAlready = false, hasNext = true))

    @Test
    fun `second http failure skips`() =
        assertEquals(Recovery.SKIP, trackRecovery(ERROR_CODE_IO_BAD_HTTP_STATUS, null, refreshedAlready = true, hasNext = true))

    @Test
    fun `dead song skips to the next`() =
        assertEquals(Recovery.SKIP, trackRecovery(ERROR_CODE_IO_UNSPECIFIED, gone, refreshedAlready = false, hasNext = true))

    @Test
    fun `dead last song stops`() =
        assertEquals(Recovery.STOP, trackRecovery(ERROR_CODE_IO_UNSPECIFIED, gone, refreshedAlready = false, hasNext = false))

    @Test
    fun `unreachable service stops instead of skipping`() {
        listOf(SourceException.Unavailable("offline"), SourceException.RateLimited("busy")).forEach {
            assertEquals(Recovery.STOP, trackRecovery(ERROR_CODE_IO_UNSPECIFIED, it, refreshedAlready = false, hasNext = true))
        }
    }

    @Test
    fun `network loss stops instead of skipping`() =
        assertEquals(Recovery.STOP, trackRecovery(ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, refreshedAlready = false, hasNext = true))
}
