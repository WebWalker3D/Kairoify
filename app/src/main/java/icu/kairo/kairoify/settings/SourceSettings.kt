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
package icu.kairo.kairoify.settings

import android.content.SharedPreferences
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.source.protocols.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The person's source switches and API keys, kept on the phone. */
class SourceSettings(private val prefs: SharedPreferences) {
    private val _changes = MutableStateFlow(0)

    /** Bumps on every change, so screens and lists can refresh. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun isOn(id: String, default: Boolean): Boolean = prefs.getBoolean("on.$id", default)

    fun setOn(id: String, on: Boolean) {
        prefs.edit().putBoolean("on.$id", on).apply()
        _changes.value++
    }

    fun key(name: String): String = prefs.getString("key.$name", "").orEmpty().trim()

    fun setKey(name: String, value: String) {
        prefs.edit().putString("key.$name", value.trim()).apply()
        _changes.value++
    }

    /** Web addresses saved under Links: playlists, feeds, streams and files. Newest first. */
    val links: List<String>
        get() = prefs.getString(LINKS, null)?.split('\n')?.filter(String::isNotBlank).orEmpty()

    fun addLink(url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        prefs.edit().putString(LINKS, (listOf(clean) + links.filterNot { it == clean }).joinToString("\n")).apply()
        _changes.value++
    }

    fun removeLink(url: String) {
        prefs.edit().putString(LINKS, links.filterNot { it == url }.joinToString("\n")).apply()
        _changes.value++
    }

    /** The person's own music servers (Subsonic/Ampache, WebDAV), passwords included. Kept only in this app's storage. */
    val servers: List<Server>
        get() = Server.decodeAll(prefs.getString(SERVERS, null))

    /** Adds [server], replacing one with the same id (same kind, address and user). */
    fun saveServer(server: Server) {
        prefs.edit().putString(SERVERS, Server.encodeAll(servers.filterNot { it.id == server.id } + server)).apply()
        _changes.value++
    }

    fun removeServer(id: String) {
        prefs.edit().putString(SERVERS, Server.encodeAll(servers.filterNot { it.id == id })).apply()
        _changes.value++
    }

    private companion object {
        const val LINKS = "links"
        const val SERVERS = "servers"
    }
}

/**
 * One source as Settings shows it.
 * @param defaultOn whether it's on before the person touches its switch.
 * @param keyName set for sources whose API needs a registered key; they stay off until there is one.
 * @param keyFallback a key built into the app, used when none is saved.
 * @param note what the person should know: how it's reached, and whether the provider authorizes it.
 */
data class SourceEntry(
    val source: MusicSource,
    val defaultOn: Boolean,
    val keyName: String? = null,
    val keyFallback: String = "",
    val note: String,
    val category: SourceCategory = SourceCategory.MUSIC,
)

/** Every source with its switch and key: the one place that decides what's on. */
class SourceCatalog(val entries: List<SourceEntry>, private val settings: SourceSettings) {
    /** Whether [source] belongs to [category]. */
    fun inCategory(source: icu.kairo.kairoify.core.source.MusicSource, category: SourceCategory): Boolean =
        entries.firstOrNull { it.source === source }?.category == category

    fun entry(id: String): SourceEntry? = entries.firstOrNull { it.source.info.id == id }

    fun key(name: String): String =
        settings.key(name).ifBlank { entries.firstOrNull { it.keyName == name }?.keyFallback.orEmpty() }

    fun isSwitchedOn(entry: SourceEntry): Boolean = settings.isOn(entry.source.info.id, entry.defaultOn)

    fun isEnabled(source: MusicSource): Boolean {
        val entry = entries.firstOrNull { it.source === source } ?: return true
        return isSwitchedOn(entry) && (entry.keyName == null || key(entry.keyName).isNotBlank())
    }
}
