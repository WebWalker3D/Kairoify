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
package icu.kairo.kairoify.core.source

/**
 * A small in-memory cache for search results and album details, so going back to a search, retyping the same words,
 * or reopening an album doesn't ask every service again. Entries expire after [ttlMillis]; past [maxEntries] the least
 * recently used one is dropped. Safe to use from several coroutines at once.
 */
class SearchCache(
    private val ttlMillis: Long = 10 * 60_000L,
    private val maxEntries: Int = 300,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val value: Any, val storedAt: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T : Any> get(key: String): T? {
        val entry = entries[key] ?: return null
        if (now() - entry.storedAt > ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.value as T
    }

    @Synchronized
    fun put(key: String, value: Any) {
        entries[key] = Entry(value, now())
    }

    companion object {
        /** "  Blues  Rock " and "blues rock" are the same search. */
        fun normalize(query: String): String = query.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
