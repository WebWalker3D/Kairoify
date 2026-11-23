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
package icu.kairo.kairoify.source.radiobrowser

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Station

internal const val SOURCE_ID = "radiobrowser"

fun StationDto.toStation(): Station {
    val tagList = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val subtitle = (listOfNotNull(country.trim().takeIf { it.isNotEmpty() }) + tagList.take(2))
        .joinToString(" · ")
        .ifEmpty { null }
    return Station(
        ref = MediaRef(SOURCE_ID, stationuuid),
        title = name.trim().ifEmpty { "Unnamed station" },
        subtitle = subtitle,
        artworkUrl = favicon.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") },
        countryCode = countrycode.trim().takeIf { it.isNotEmpty() },
        tags = tagList,
        codec = codec.trim().takeIf { it.isNotEmpty() },
        bitrateKbps = bitrate.takeIf { it > 0 },
        homepage = homepage.trim().takeIf { it.isNotEmpty() },
        streamUrl = urlResolved.trim().ifEmpty { url.trim() },
        isHls = hls == 1,
    )
}
