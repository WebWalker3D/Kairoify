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
package icu.kairo.kairoify.source.protocols

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** The kinds of server a person can add. UPnP/DLNA servers aren't added: they're found on the network. */
@Serializable
enum class ServerKind(val label: String) {
    SUBSONIC("Subsonic / OpenSubsonic / Ampache"),
    WEBDAV("WebDAV folder"),
}

/**
 * A server the person added in Settings. Kept only on the phone. [url] is the server's address as they typed it;
 * for Ampache that's the server itself, which answers Subsonic clients at /rest.
 */
@Serializable
data class Server(
    val kind: ServerKind,
    val name: String,
    val url: String,
    val user: String = "",
    val password: String = "",
) {
    /** Stable while the address and user don't change, so favorites keep pointing at the right server. */
    val id: String get() = sha1("${kind.name}|${url.trimEnd('/')}|$user").take(10)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun encodeAll(servers: List<Server>): String = json.encodeToString(ListSerializer(serializer()), servers)
        fun decodeAll(text: String?): List<Server> =
            text?.let { runCatching { json.decodeFromString(ListSerializer(serializer()), it) }.getOrNull() }.orEmpty()
    }
}

internal fun sha1(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

internal fun md5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

/** "serverId|rest" item ids, so one source can hold many servers. */
internal fun serverRef(server: Server, rest: String) = "${server.id}|$rest"

internal fun splitServerRef(itemId: String): Pair<String, String> = itemId.substringBefore('|') to itemId.substringAfter('|', "")
