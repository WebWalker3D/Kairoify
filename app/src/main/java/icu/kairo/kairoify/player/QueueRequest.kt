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

import androidx.media3.common.Player
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.Station
import kotlin.random.Random

/** Everything the player needs to start a queue. */
data class QueueRequest(val items: List<Item>, val startIndex: Int, val shuffle: Boolean, val repeatMode: Int)

/**
 * Builds a queue. Shuffle starts on a random item. A queue of only stations wraps around at both ends, like the
 * preset buttons on a car radio.
 */
fun queueRequest(items: List<Item>, startIndex: Int, shuffle: Boolean, random: Random = Random.Default): QueueRequest {
    require(items.isNotEmpty()) { "A queue needs at least one item" }
    val start = if (shuffle) random.nextInt(items.size) else startIndex.coerceIn(items.indices)
    val presets = items.size > 1 && items.all { it is Station }
    return QueueRequest(items, start, shuffle, if (presets) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF)
}
