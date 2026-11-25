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
package icu.kairo.kairoify.core.transcribe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * The speech-recognition model, downloaded once when first needed and kept in the app's own storage. The file is
 * only used once its SHA-256 matches the pinned one; anything else is deleted.
 */
class SpeechModel(
    private val client: OkHttpClient,
    private val dir: File,
    private val url: String = BASE_Q5_URL,
    private val sha256: String = BASE_Q5_SHA256,
    val sizeBytes: Long = BASE_Q5_BYTES,
) {
    private val target = File(dir, url.substringAfterLast('/'))

    val isReady: Boolean get() = target.isFile

    /** The model file, downloading it first if needed; [progress] gets 0..1 while downloading. */
    suspend fun file(progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        if (target.isFile) return@withContext target
        dir.mkdirs()
        val part = File(dir, target.name + ".part")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Couldn't download the speech model (HTTP ${response.code}).")
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: sizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                part.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            progress((done.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (hash != sha256) throw IOException("The speech model didn't download correctly. Try again.")
            }
            if (!part.renameTo(target)) throw IOException("Couldn't save the speech model.")
            progress(1f)
            target
        } finally {
            part.delete()
        }
    }

    companion object {
        /** whisper.cpp's multilingual "base" model, 5-bit quantized (MIT licensed, ggerganov/whisper.cpp on Hugging Face). */
        const val BASE_Q5_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
        const val BASE_Q5_SHA256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
        const val BASE_Q5_BYTES = 59_707_625L
    }
}
