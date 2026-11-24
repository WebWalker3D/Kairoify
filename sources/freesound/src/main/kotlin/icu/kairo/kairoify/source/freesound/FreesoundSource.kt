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
package icu.kairo.kairoify.source.freesound

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.model.licenseFromUrl
import icu.kairo.kairoify.core.network.fetchText
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class FsSound(
    val id: Long? = null,
    val name: String? = null,
    val username: String? = null,
    val duration: Double? = null,
    val license: String? = null,
    val url: String? = null,
    val previews: Map<String, String> = emptyMap(),
)

@Serializable
internal data class FsReply(val results: List<FsSound> = emptyList())

/** Freesound: experimental, ambient and musical recordings. Its API needs a free key, entered in Settings. */
class FreesoundSource(
    private val client: OkHttpClient,
    private val apiKey: () -> String,
    private val baseUrl: HttpUrl = "https://freesound.org/apiv2/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "Freesound")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val url = api("search/text/").addQueryParameter("query", query).addQueryParameter("page_size", "30")
            .addQueryParameter("fields", FIELDS).build()
        return Page(decode(url, FsReply.serializer()).results.mapNotNull(::track), next = null)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val sound = decode(api("sounds/${ref.itemId}/").addQueryParameter("fields", FIELDS).build(), FsSound.serializer())
        val preview = sound.previews[HQ]?.takeIf(String::isNotBlank) ?: throw SourceException.NotFound("That sound is no longer on Freesound.")
        return ResolvedStream(url = preview, isLive = false, license = licenseFromUrl(sound.license))
    }

    private fun api(path: String) = baseUrl.newBuilder().addPathSegments(path).addQueryParameter("token", apiKey())

    private suspend fun <T> decode(url: HttpUrl, serializer: KSerializer<T>): T = try {
        json.decodeFromString(serializer, client.fetchText(url, info.displayName))
    } catch (e: IllegalArgumentException) {
        throw SourceException.Unavailable("Couldn't reach Freesound.", e)
    }

    private fun track(s: FsSound): Track? {
        val id = s.id ?: return null
        val title = s.name?.substringBeforeLast('.')?.takeIf(String::isNotBlank) ?: return null
        if (s.previews[HQ].isNullOrBlank()) return null
        return Track(
            ref = MediaRef(ID, id.toString()),
            title = title,
            subtitle = s.username,
            artists = listOfNotNull(s.username),
            durationMillis = s.duration?.let { (it * 1000).toLong() },
            pageUrl = s.url,
            license = licenseFromUrl(s.license),
        )
    }

    companion object {
        const val ID = "freesound"
        private const val HQ = "preview-hq-mp3"
        private const val FIELDS = "id,name,username,duration,license,url,previews"
    }
}
