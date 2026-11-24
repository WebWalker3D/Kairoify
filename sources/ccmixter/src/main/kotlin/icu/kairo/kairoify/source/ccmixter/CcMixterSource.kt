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
package icu.kairo.kairoify.source.ccmixter

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class CcFormat(val ps: String? = null)

@Serializable
internal data class CcFile(
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("file_format_info") val formatInfo: CcFormat? = null,
)

@Serializable
internal data class CcUpload(
    @SerialName("upload_id") val uploadId: Long? = null,
    @SerialName("upload_name") val uploadName: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_real_name") val userRealName: String? = null,
    @SerialName("file_page_url") val filePageUrl: String? = null,
    @SerialName("license_url") val licenseUrl: String? = null,
    val files: List<CcFile> = emptyList(),
)

/** ccMixter: Creative Commons remixes and originals. Its query API welcomes third-party apps. */
class CcMixterSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://ccmixter.org/api/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "ccMixter")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ccMixter's API answers nothing for 20 results; 10 works.
    override suspend fun search(query: String, page: PageToken?): Page<Item> =
        Page(query(mapOf("search" to query, "limit" to "10")).mapNotNull(::track), next = null)

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val upload = query(mapOf("ids" to ref.itemId)).firstOrNull { it.uploadId?.toString() == ref.itemId }
        val mp3 = upload?.let(::mp3)?.downloadUrl ?: throw SourceException.NotFound("That track is no longer on ccMixter.")
        // ccMixter's files only play for requests from its own pages (hotlink protection). Off by default; the person
        // switches it on in Settings knowing that.
        val referer = upload.filePageUrl ?: "https://ccmixter.org/"
        return ResolvedStream(url = mp3, headers = mapOf("Referer" to referer), isLive = false, license = licenseFromUrl(upload.licenseUrl))
    }

    private suspend fun query(params: Map<String, String>): List<CcUpload> {
        val url = baseUrl.newBuilder().addPathSegment("query")
            .addQueryParameter("f", "json").addQueryParameter("dataview", "default")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val text = client.fetchText(url, info.displayName)
        if (text.isBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(CcUpload.serializer()), text)
        } catch (e: IllegalArgumentException) {
            throw SourceException.Unavailable("Couldn't reach ccMixter.", e)
        }
    }

    private fun mp3(upload: CcUpload): CcFile? =
        upload.files.firstOrNull { it.fileName.orEmpty().lowercase().endsWith(".mp3") && !it.downloadUrl.isNullOrBlank() }

    private fun track(u: CcUpload): Track? {
        val id = u.uploadId ?: return null
        val title = u.uploadName?.takeIf(String::isNotBlank) ?: return null
        val file = mp3(u) ?: return null
        val artist = u.userRealName?.takeIf(String::isNotBlank) ?: u.userName
        return Track(
            ref = MediaRef(ID, id.toString()),
            title = title,
            subtitle = artist,
            artists = listOfNotNull(artist),
            durationMillis = parseClock(file.formatInfo?.ps),
            pageUrl = u.filePageUrl,
            license = licenseFromUrl(u.licenseUrl),
        )
    }

    companion object {
        const val ID = "ccmixter"

        /** "3:17" → milliseconds. */
        fun parseClock(text: String?): Long? {
            val parts = text?.split(":")?.map { it.trim().toLongOrNull() ?: return null } ?: return null
            if (parts.size !in 2..3) return null
            return parts.fold(0L) { total, p -> total * 60 + p } * 1000
        }
    }
}
