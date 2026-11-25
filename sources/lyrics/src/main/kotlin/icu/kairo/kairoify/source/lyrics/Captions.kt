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
package icu.kairo.kairoify.source.lyrics

import icu.kairo.kairoify.core.network.fetchText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class CommonsPage(val title: String)

@Serializable
internal data class CommonsPages(val allpages: List<CommonsPage> = emptyList())

@Serializable
internal data class CommonsList(val query: CommonsPages? = null)

@Serializable
internal data class ArchiveFile(val name: String)

@Serializable
internal data class ArchiveMetadata(val files: List<ArchiveFile> = emptyList())

/**
 * Captions the archives publish themselves: Wikimedia Commons' TimedText subtitles and the Internet Archive's
 * .srt/.vtt files. Looked up only for the recording playing, only when asked. Null when there are none.
 */
class CaptionFinder(
    private val client: OkHttpClient,
    private val commons: HttpUrl = "https://commons.wikimedia.org/w/".toHttpUrl(),
    private val archive: HttpUrl = "https://archive.org/".toHttpUrl(),
    private val language: () -> String = { java.util.Locale.getDefault().language },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Subtitles for a Commons file ("File:Speech.ogg"), in the phone's language when there, else English, else any. */
    suspend fun wikimedia(fileTitle: String): Lyrics? = quietly {
        val file = fileTitle.removePrefix("File:")
        val list = commons.newBuilder().addPathSegment("api.php")
            .addQueryParameter("action", "query").addQueryParameter("format", "json")
            .addQueryParameter("list", "allpages").addQueryParameter("apnamespace", "102")
            .addQueryParameter("apprefix", "$file.").addQueryParameter("aplimit", "100").build()
        val pages = json.decodeFromString(CommonsList.serializer(), client.fetchText(list, "Wikimedia Commons"))
            .query?.allpages.orEmpty().map { it.title }.filter { it.endsWith(".srt") || it.endsWith(".vtt") }
        val page = pick(pages) { it.substringBeforeLast('.').substringAfterLast('.') } ?: return@quietly null
        val raw = commons.newBuilder().addPathSegment("index.php")
            .addQueryParameter("title", page).addQueryParameter("action", "raw").build()
        captions(client.fetchText(raw, "Wikimedia Commons"), "Captions from Wikimedia Commons")
    }

    /** Subtitles for an Internet Archive file ("identifier/path/name.mp3") published beside it in the same item. */
    suspend fun internetArchive(itemPath: String): Lyrics? = quietly {
        val identifier = itemPath.substringBefore('/')
        val stem = itemPath.substringAfter('/', "").substringBeforeLast('.').takeIf(String::isNotBlank) ?: return@quietly null
        val meta = archive.newBuilder().addPathSegment("metadata").addPathSegment(identifier).build()
        val files = json.decodeFromString(ArchiveMetadata.serializer(), client.fetchText(meta, "Internet Archive")).files
            .map { it.name }.filter { (it.endsWith(".srt") || it.endsWith(".vtt")) && it.startsWith("$stem.") }
        // Captions written by people before the Archive's own automatic ones (".asr.srt").
        val name = pick(files.sortedBy { ".asr." in it }) { it.removePrefix("$stem.").substringBeforeLast('.').substringAfterLast('.') }
            ?: return@quietly null
        val url = archive.newBuilder().addPathSegment("download").addPathSegment(identifier)
            .apply { name.split('/').forEach(::addPathSegment) }.build()
        val credit = if (".asr." in name) "Automatic captions from the Internet Archive" else "Captions from the Internet Archive"
        captions(client.fetchText(url, "Internet Archive"), credit)
    }

    /** The phone's language first, then English, then whatever there is. */
    private fun pick(names: List<String>, lang: (String) -> String): String? {
        val mine = language()
        return names.firstOrNull { lang(it) == mine } ?: names.firstOrNull { lang(it) == "en" } ?: names.firstOrNull()
    }

    private fun captions(text: String, credit: String): Lyrics? =
        parseSubtitles(text).takeIf { it.isNotEmpty() }?.let { Lyrics(it, plain = null, credit = credit) }

    private suspend fun quietly(block: suspend () -> Lyrics?): Lyrics? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

private val CUE_TIME = Regex("^\\s*(?:(\\d+):)?(\\d{1,2}):(\\d{2})[,.](\\d{1,3})\\s*-->")

/** Reads SRT or WebVTT: one line per cue, at the cue's start, with formatting tags removed. */
fun parseSubtitles(text: String): List<LyricLine> =
    text.replace("\r\n", "\n").split(Regex("\n\\s*\n")).mapNotNull { block ->
        val lines = block.lines()
        val at = lines.indexOfFirst { CUE_TIME.containsMatchIn(it) }
        if (at < 0) return@mapNotNull null
        val (h, m, s, frac) = CUE_TIME.find(lines[at])!!.destructured
        val millis = ((h.toLongOrNull() ?: 0) * 3600 + m.toLong() * 60 + s.toLong()) * 1000 + frac.padEnd(3, '0').toLong()
        val words = lines.drop(at + 1).joinToString(" ") { it.trim() }
            .replace(Regex("<[^>]*>|\\{[^}]*}"), "").replace(Regex("\\s+"), " ").trim()
        words.takeIf(String::isNotEmpty)?.let { LyricLine(millis, it) }
    }.sortedBy { it.atMillis }
