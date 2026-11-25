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
package icu.kairo.kairoify.player

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.StreamMime
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.transcribe.Transcriber
import icu.kairo.kairoify.core.transcribe.Transcription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import icu.kairo.kairoify.core.source.mainArtist
import icu.kairo.kairoify.source.lyrics.CaptionFinder
import icu.kairo.kairoify.source.lyrics.LrcLib
import icu.kairo.kairoify.source.lyrics.Lyrics

/** Sources whose captions come from Wikimedia Commons or the Internet Archive. */
private const val COMMONS = "wikimedia"
private val ARCHIVE = setOf("archive", "librivox")

/**
 * Looks up the words for what's playing: captions the recording's own archive publishes first, then song lyrics
 * when there's an artist. Null when neither could exist (live radio, or nothing to look up by).
 */
fun findWords(item: Item?, isLive: Boolean, durationMs: Long?, captions: CaptionFinder, lyrics: LrcLib): (suspend () -> Lyrics?)? {
    val track = item as? Track ?: return null
    if (isLive) return null
    val ref = track.ref
    val own: (suspend () -> Lyrics?)? = when {
        ref.sourceId == COMMONS -> { { captions.wikimedia(ref.itemId) } }
        ref.sourceId in ARCHIVE && '/' in ref.itemId -> { { captions.internetArchive(ref.itemId) } }
        else -> null
    }
    val song: (suspend () -> Lyrics?)? = mainArtist(track)?.let { artist ->
        { lyrics.find(artist, track.title.replace(Regex("\\s*[(\\[].*?[)\\]]"), ""), track.durationMillis ?: durationMs) }
    }
    if (own == null && song == null) return null
    return { own?.invoke() ?: song?.invoke() }
}

/**
 * Starts writing captions on the phone for what's playing, from where it is now. Null for live streams; HLS
 * streams (YouTube and the like) can't be decoded this way and report that when tried.
 */
fun transcription(item: Item?, isLive: Boolean, registry: SourceRegistry, transcriber: Transcriber, player: PlayerConnection): (() -> Flow<Transcription>)? {
    val track = item as? Track ?: return null
    if (isLive) return null
    return {
        flow {
            val stream = registry.resolve(track.ref)
            if (stream.isLive || stream.mimeType == StreamMime.HLS || ".m3u8" in stream.url) {
                emit(Transcription.Failed("This recording streams in pieces the phone can't transcribe."))
            } else {
                emitAll(transcriber.transcribe(stream, player.position()))
            }
        }
    }
}
