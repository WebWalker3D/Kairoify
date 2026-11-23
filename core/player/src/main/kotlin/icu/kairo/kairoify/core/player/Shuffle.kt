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
package icu.kairo.kairoify.core.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/** A shuffled play order of [size] items that begins with [start] and then visits every other item once. */
fun shuffledFrom(start: Int, size: Int, random: Random = Random.Default): IntArray =
    (listOf(start) + (0 until size).filter { it != start }.shuffled(random)).toIntArray()

/**
 * Media3's default shuffle order ignores where playback is, so turning shuffle on mid-queue (or starting a shuffled
 * queue) played only the items after the current one in a random order. This makes the order start here.
 */
@OptIn(UnstableApi::class)
fun ExoPlayer.shuffleFromCurrent(random: Random = Random.Default) {
    val size = mediaItemCount
    if (size == 0) return
    setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(shuffledFrom(currentMediaItemIndex, size, random), random.nextLong()))
}
