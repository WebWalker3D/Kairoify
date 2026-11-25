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

import icu.kairo.kairoify.source.lyrics.LyricLine

/** Whisper listens at 16 kHz, one channel. */
const val WHISPER_RATE = 16_000

/** Averages interleaved channels into one and resamples (linearly) to 16 kHz. */
fun toMono16k(interleaved: FloatArray, channels: Int, sampleRate: Int): FloatArray {
    val frames = interleaved.size / channels
    val mono = FloatArray(frames) { f -> (0 until channels).sumOf { interleaved[f * channels + it].toDouble() }.toFloat() / channels }
    if (sampleRate == WHISPER_RATE) return mono
    val outSize = (frames.toLong() * WHISPER_RATE / sampleRate).toInt()
    val step = sampleRate.toDouble() / WHISPER_RATE
    return FloatArray(outSize) { i ->
        val pos = i * step
        val at = pos.toInt().coerceAtMost(frames - 1)
        val next = (at + 1).coerceAtMost(frames - 1)
        val frac = (pos - at).toFloat()
        mono[at] * (1 - frac) + mono[next] * frac
    }
}

/** 16-bit PCM to floats between -1 and 1. */
fun pcm16ToFloat(samples: ShortArray): FloatArray = FloatArray(samples.size) { samples[it] / 32768f }

private val NOT_SPEECH = Regex("^[\\[(].*[\\])]$")

/** Whisper's segments ("start\tend\ttext", times from the chunk's start) as caption lines, moved to where the chunk sits. */
fun parseSegments(raw: Array<ByteArray>, offsetMillis: Long): List<LyricLine> = raw.mapNotNull { bytes ->
    val parts = bytes.toString(Charsets.UTF_8).split('\t', limit = 3)
    val start = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
    val text = parts.getOrNull(2)?.trim().orEmpty()
    // Blank, or a sound label like "[MUSIC]" or "(applause)" rather than words.
    if (text.isEmpty() || NOT_SPEECH.matches(text)) return@mapNotNull null
    LyricLine(offsetMillis + start, text)
}
