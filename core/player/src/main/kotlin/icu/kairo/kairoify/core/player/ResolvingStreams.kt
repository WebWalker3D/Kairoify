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

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** A source couldn't turn an item into a stream. [reason] says why, in words the person can read. */
class ResolveException(val reason: SourceException) : IOException(reason.message, reason)

/** The [ResolveException] somewhere in this error's causes, if any. */
fun Throwable.resolveFailure(): ResolveException? = generateSequence(this) { it.cause }.filterIsInstance<ResolveException>().firstOrNull()

/**
 * Swaps a Kairoify placeholder for the real stream, at the moment the player first needs audio from it.
 * Headers already on the request (such as ICY metadata) are kept; the source's own headers are added.
 */
@OptIn(UnstableApi::class)
fun resolveDataSpec(dataSpec: DataSpec, resolve: (MediaRef) -> ResolvedStream): DataSpec {
    val ref = MediaItems.refOfPlaceholder(dataSpec.uri) ?: return dataSpec
    val stream = try {
        resolve(ref)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SourceException) {
        throw ResolveException(e)
    } catch (e: Exception) {
        throw ResolveException(SourceException.Unavailable(e.message ?: "Couldn't reach the service", e))
    }
    return dataSpec.withUri(stream.url.toUri()).withAdditionalHeaders(stream.headers)
}

/** Retries loads as usual, except a failed resolve: the source already said why, and asking again only delays the answer. */
@OptIn(UnstableApi::class)
class ResolveAwareLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (loadErrorInfo.exception.resolveFailure() != null) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
}
