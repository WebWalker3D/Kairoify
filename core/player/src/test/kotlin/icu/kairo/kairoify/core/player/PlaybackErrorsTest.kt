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

import androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED
import org.junit.Test
import kotlin.test.assertEquals

class PlaybackErrorsTest {
    @Test
    fun `dead stream says not broadcasting`() {
        listOf(ERROR_CODE_IO_BAD_HTTP_STATUS, ERROR_CODE_IO_FILE_NOT_FOUND).forEach {
            assertEquals("This station isn't broadcasting right now.", playbackErrorMessage(it))
        }
    }

    @Test
    fun `unsupported format says phone can't play`() {
        listOf(
            ERROR_CODE_PARSING_CONTAINER_MALFORMED, ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED, ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, ERROR_CODE_DECODER_INIT_FAILED,
        ).forEach { assertEquals("This station uses a format your phone can't play.", playbackErrorMessage(it)) }
    }

    @Test
    fun `network trouble says lost the connection`() {
        listOf(ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT).forEach {
            assertEquals("Lost the connection. Check your internet and try again.", playbackErrorMessage(it))
        }
    }

    @Test
    fun `anything else is generic`() {
        assertEquals("Couldn't play this station.", playbackErrorMessage(ERROR_CODE_UNSPECIFIED))
    }
}
