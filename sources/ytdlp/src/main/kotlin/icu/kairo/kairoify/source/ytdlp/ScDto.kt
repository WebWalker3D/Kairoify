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

@Serializable
internal data class ScThumb(val id: String? = null, val url: String? = null, val width: Int? = null)

/** One entry of yt-dlp's flat "scsearch" result. */
@Serializable
internal data class ScEntry(
    val title: String? = null,
    val duration: Double? = null,
    @SerialName("webpage_url") val webpageUrl: String? = null,
    val uploader: String? = null,
    val artists: List<String>? = null,
    val thumbnails: List<ScThumb> = emptyList(),
)
