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

import android.content.Context
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.source.lyrics.LyricLine
import icu.kairo.kairoify.source.lyrics.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** How on-phone transcription is going. */
sealed interface Transcription {
    /** Fetching the speech model, the first time only. */
    data class Downloading(val fraction: Float) : Transcription

    /** Captions so far; [throughMillis] is how far into the recording they reach. */
    data class Writing(val captions: Lyrics, val throughMillis: Long, val finished: Boolean) : Transcription

    data class Failed(val message: String) : Transcription
}

/**
 * Writes captions on the phone with whisper.cpp: decodes the recording from where it's playing, 30 seconds at a
 * time, and turns each stretch into timed lines. Nothing is sent anywhere. One recording at a time.
 */
class Transcriber(context: Context, private val model: SpeechModel) {
    private val decoder = AudioDecoder(context.applicationContext)
    private val busy = Mutex()
    private var handle = 0L

    val modelReady: Boolean get() = model.isReady
    val modelMegabytes: Long get() = model.sizeBytes / 1_000_000

    fun transcribe(stream: ResolvedStream, fromMillis: Long, language: String = "auto"): Flow<Transcription> = channelFlow {
        try {
            busy.withLock {
                if (handle == 0L) {
                    val file = model.file { trySend(Transcription.Downloading(it)) }
                    handle = withContext(Dispatchers.Default) { WhisperNative.load(file.path) }
                    if (handle == 0L) {
                        file.delete()
                        throw IOException("The speech model couldn't be loaded. It will download again next time.")
                    }
                }
                val lines = mutableListOf<LyricLine>()
                var through = fromMillis
                send(Transcription.Writing(captions(lines), through, finished = false))
                decoder.chunks(stream, fromMillis).collect { chunk ->
                    val raw = withContext(Dispatchers.Default) { WhisperNative.transcribe(handle, chunk.samples, language, THREADS) }
                        ?: throw IOException("Transcription stopped partway.")
                    lines += parseSegments(raw, chunk.startMillis)
                    through = chunk.startMillis + chunk.samples.size * 1000L / WHISPER_RATE
                    send(Transcription.Writing(captions(lines), through, finished = false))
                }
                send(Transcription.Writing(captions(lines), through, finished = true))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            send(Transcription.Failed(e.message ?: "Couldn't transcribe this recording."))
        }
    }

    private fun captions(lines: List<LyricLine>) = Lyrics(lines.toList(), plain = null, credit = CREDIT)

    companion object {
        const val CREDIT = "Transcribed on this phone by Whisper; may contain mistakes"
        private val THREADS = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
    }
}
