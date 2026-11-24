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
package icu.kairo.kairoify.player

import android.content.SharedPreferences
import icu.kairo.kairoify.core.model.MediaRef

/**
 * Where the person left each track, so playing it again picks up there. Kept on the phone for the [maxEntries] most
 * recently left items. Live radio never comes here.
 */
class ResumePositions(private val prefs: SharedPreferences, private val maxEntries: Int = 200) {
    fun positionFor(ref: MediaRef): Long? = prefs.getLong(key(ref), -1).takeIf { it >= 0 }

    /** Saves [positionMs], unless it's so near the start or the end that starting over is the right thing. */
    fun remember(ref: MediaRef, positionMs: Long, durationMs: Long?) {
        val nearEnd = durationMs != null && durationMs > 0 && positionMs >= durationMs - END_MS
        if (positionMs < START_MS || nearEnd) {
            forget(ref)
            return
        }
        val order = order().filter { it != key(ref) } + key(ref)
        val dropped = order.dropLast(maxEntries)
        prefs.edit().apply {
            putLong(key(ref), positionMs)
            dropped.forEach { remove(it) }
            putString(ORDER, order.takeLast(maxEntries).joinToString(SEP))
        }.apply()
    }

    fun forget(ref: MediaRef) {
        prefs.edit().remove(key(ref)).putString(ORDER, order().filter { it != key(ref) }.joinToString(SEP)).apply()
    }

    private fun order(): List<String> = prefs.getString(ORDER, "").orEmpty().split(SEP).filter(String::isNotEmpty)

    private fun key(ref: MediaRef) = "pos:${ref.sourceId}:${ref.itemId}"

    private companion object {
        const val START_MS = 5_000L
        const val END_MS = 10_000L
        const val ORDER = "order"
        const val SEP = "\n"
    }
}
