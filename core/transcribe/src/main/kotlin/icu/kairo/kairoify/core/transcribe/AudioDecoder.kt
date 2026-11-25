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
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import icu.kairo.kairoify.core.model.ResolvedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** A stretch of 16 kHz mono audio and where in the recording it starts. */
class AudioChunk(val startMillis: Long, val samples: FloatArray)

/**
 * Decodes a recording (a file URL or content:// URI, not HLS) with Android's own decoders, from [fromMillis] on,
 * in chunks of [chunkMillis] ready for whisper.
 */
class AudioDecoder(private val context: Context) {
    fun chunks(stream: ResolvedStream, fromMillis: Long, chunkMillis: Long = 30_000): Flow<AudioChunk> = flow {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(stream.url), stream.headers)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: throw IOException("This recording has no audio track Android can read.")
            extractor.selectTrack(track)
            extractor.seekTo(fromMillis * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floats = false
            val pending = ArrayList<FloatArray>()
            var pendingSize = 0
            var chunkStart = -1L
            val chunkSamples = (chunkMillis * WHISPER_RATE / 1000).toInt()
            val info = MediaCodec.BufferInfo()
            var inputDone = false

            suspend fun emitPending(force: Boolean) {
                while (pendingSize >= chunkSamples || (force && pendingSize > 0)) {
                    val all = FloatArray(pendingSize).also { out -> var at = 0; pending.forEach { it.copyInto(out, at); at += it.size } }
                    val take = minOf(chunkSamples, all.size)
                    emit(AudioChunk(chunkStart, all.copyOf(take)))
                    chunkStart += take * 1000L / WHISPER_RATE
                    pending.clear()
                    pendingSize = 0
                    if (all.size > take) all.copyOfRange(take, all.size).let { pending += it; pendingSize = it.size }
                }
            }

            while (true) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val size = extractor.readSampleData(decoder.getInputBuffer(inIndex)!!, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> decoder.outputFormat.let {
                        rate = it.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floats = it.containsKey(MediaFormat.KEY_PCM_ENCODING) && it.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        if (chunkStart < 0) chunkStart = maxOf(fromMillis, info.presentationTimeUs / 1000)
                        val buffer = decoder.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        buffer.position(info.offset).limit(info.offset + info.size)
                        val raw = if (floats) {
                            FloatArray(info.size / 4).also { buffer.asFloatBuffer().get(it) }
                        } else {
                            pcm16ToFloat(ShortArray(info.size / 2).also { buffer.asShortBuffer().get(it) })
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (raw.isNotEmpty()) toMono16k(raw, channels, rate).let { pending += it; pendingSize += it.size }
                        emitPending(force = false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            emitPending(force = true)
                            break
                        }
                    }
                }
            }
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }.flowOn(Dispatchers.IO)
}
