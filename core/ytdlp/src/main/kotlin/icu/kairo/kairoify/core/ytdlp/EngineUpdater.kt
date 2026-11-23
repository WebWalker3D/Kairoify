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
package icu.kairo.kairoify.core.ytdlp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

sealed interface UpdateResult {
    /** Checked too recently; nothing was asked. */
    data object NotDue : UpdateResult

    data class UpToDate(val version: String) : UpdateResult

    /** A newer yt-dlp is on the phone and checked; it runs from the next app start. */
    data class Downloaded(val version: String) : UpdateResult

    data class Failed(val message: String) : UpdateResult
}

/**
 * Keeps yt-dlp current without an app release: YouTube changes often, and yt-dlp's fixes ship as new releases.
 * Downloads come only from yt-dlp's official GitHub releases, are checked against the release's SHA-256 sums, and
 * take effect the next time Kairoify starts. The bundled copy is always there to fall back to.
 */
class EngineUpdater(
    private val client: OkHttpClient,
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val apiBase: HttpUrl = "https://api.github.com/".toHttpUrl(),
    private val downloadBase: HttpUrl = "https://github.com/".toHttpUrl(),
    private val bundledVersion: String = BUNDLED_YTDLP_VERSION,
) {
    @Serializable
    private data class State(
        val active: String? = null,
        val lastCheckMillis: Long = 0,
        val lastForcedMillis: Long = 0,
        val bad: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val stateFile = File(dir, "state.json")
    private val lock = Mutex()

    /** The downloaded yt-dlp the next start will load: newer than the built-in one and never failed before. */
    fun activeUpdate(): File? = activeVersion()?.let { zipFor(it) }?.takeIf(File::isFile)

    fun activeVersion(): String? =
        load().let { s -> s.active?.takeIf { it !in s.bad && compareVersions(it, bundledVersion) > 0 } }

    /**
     * Called once as the engine starts, before Python loads anything: clears out zips nothing will use (older
     * downloads, rolled-back or failed ones) and returns the one to load. Zips are only ever deleted here, because
     * Python keeps reading modules from the loaded zip for as long as the app runs.
     */
    fun updateToLoad(): File? {
        val keep = activeUpdate()
        dir.listFiles { f -> f.name.startsWith("yt-dlp-") && (f.name.endsWith(".zip") || f.name.endsWith(".part")) && f != keep }
            .orEmpty()
            .forEach { it.delete() }
        if (keep == null && load().active != null) save(load().copy(active = null))
        return keep
    }

    /** Checks GitHub for a newer yt-dlp: at most daily, or hourly when [force]d (a source just broke). */
    suspend fun check(force: Boolean = false): UpdateResult = lock.withLock {
        withContext(Dispatchers.IO) {
            val state = load()
            val time = now()
            val due = if (force) time - state.lastForcedMillis >= HOUR else time - state.lastCheckMillis >= DAY
            if (!due) return@withContext UpdateResult.NotDue
            save(state.copy(lastCheckMillis = time, lastForcedMillis = if (force) time else state.lastForcedMillis))
            try {
                download(state)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A captive-portal page, a GitHub hiccup, a full disk: say so, never crash.
                UpdateResult.Failed("Couldn't check for an update.")
            }
        }
    }

    /** Back to the yt-dlp inside the APK, from the next start (the running zip is removed then, not now). */
    fun rollback() {
        save(load().copy(active = null))
    }

    /** A downloaded version that wouldn't load: never use or download it again. Its file goes at the next start. */
    fun markBad(version: String) {
        val state = load()
        save(state.copy(active = state.active?.takeIf { it != version }, bad = (state.bad + version).distinct()))
    }

    private fun download(state: State): UpdateResult {
        val tag = latestTag()
        if (!TAG.matches(tag)) return UpdateResult.Failed("Unexpected engine version")
        val current = listOfNotNull(bundledVersion, state.active?.takeIf { it !in state.bad }).maxWithOrNull(::compareVersions)!!
        if (compareVersions(tag, current) <= 0 || tag in state.bad) return UpdateResult.UpToDate(current)

        val sums = fetch(release(tag, "SHA2-256SUMS")).decodeToString()
        val expected = sums.lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size == 2 && it[1] == "yt-dlp" }
            ?.first()?.lowercase()
            ?: return UpdateResult.Failed("The engine update has no checksum")
        val bytes = fetch(release(tag, "yt-dlp"))
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (actual != expected) return UpdateResult.Failed("Engine download didn't match its checksum")

        dir.mkdirs()
        val part = File(dir, "yt-dlp-$tag.part").apply { writeBytes(bytes) }
        if (!part.renameTo(zipFor(tag))) {
            part.delete()
            return UpdateResult.Failed("Couldn't save the engine update")
        }
        save(load().copy(active = tag))
        return UpdateResult.Downloaded(tag)
    }

    private fun latestTag(): String {
        val body = fetch(apiBase.resolve("repos/yt-dlp/yt-dlp/releases/latest")!!, accept = "application/vnd.github+json").decodeToString()
        return (Json.parseToJsonElement(body).jsonObject["tag_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    /** Release files are always fetched from a URL built here, never from links in GitHub's reply. */
    private fun release(tag: String, file: String): HttpUrl = downloadBase.resolve("yt-dlp/yt-dlp/releases/download/$tag/$file")!!

    private fun fetch(url: HttpUrl, accept: String? = null): ByteArray {
        val request = Request.Builder().url(url).apply { accept?.let { header("Accept", it) } }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val length = response.body.contentLength()
            if (length > MAX_BYTES) throw IOException("Too large: $length bytes")
            // A plain read loop: InputStream.readNBytes only exists from Android 13.
            val out = java.io.ByteArrayOutputStream()
            response.body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("Too large")
                }
            }
            return out.toByteArray()
        }
    }

    private fun zipFor(version: String) = File(dir, "yt-dlp-$version.zip")

    private fun load(): State =
        runCatching { json.decodeFromString(State.serializer(), stateFile.readText()) }.getOrDefault(State())

    private fun save(state: State) {
        dir.mkdirs()
        val tmp = File(dir, "state.json.tmp")
        tmp.writeText(json.encodeToString(State.serializer(), state))
        if (!tmp.renameTo(stateFile)) {
            stateFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        const val MAX_BYTES = 30 * 1024 * 1024
        val TAG = Regex("^\\d{4}\\.\\d{1,2}\\.\\d{1,2}(\\.\\d+)?$")
    }
}
