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

import androidx.media3.common.PlaybackException
import icu.kairo.kairoify.core.source.SourceException

/** What to do when a song (not a live station) fails. */
enum class Recovery { REFRESH_URL, SKIP, STOP }

/**
 * - An HTTP error on a stream that played before usually means its URL expired: fetch a fresh one, once.
 * - A song that is gone, geoblocked or can't be extracted is skipped, so Play all keeps going.
 * - When the service or the network is unreachable, skipping would just fail through the whole queue, so stop.
 */
fun trackRecovery(errorCode: Int, resolveFailure: SourceException?, refreshedAlready: Boolean, hasNext: Boolean): Recovery = when {
    resolveFailure == null && LiveRetryPolicy.isNetworkError(errorCode) -> Recovery.STOP
    resolveFailure == null && errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && !refreshedAlready -> Recovery.REFRESH_URL
    resolveFailure is SourceException.Unavailable || resolveFailure is SourceException.RateLimited -> Recovery.STOP
    hasNext -> Recovery.SKIP
    else -> Recovery.STOP
}
