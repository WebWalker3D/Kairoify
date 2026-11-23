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
package icu.kairo.kairoify.source.ytdlp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One result from Bandcamp's autocomplete search. */
@Serializable
data class BcResult(
    val type: String? = null,
    val name: String? = null,
    @SerialName("band_name") val bandName: String? = null,
    @SerialName("album_name") val albumName: String? = null,
    @SerialName("item_url_path") val itemUrlPath: String? = null,
    val img: String? = null,
)

@Serializable
internal data class BcAuto(val results: List<BcResult> = emptyList())

@Serializable
internal data class BcReply(val auto: BcAuto = BcAuto())
