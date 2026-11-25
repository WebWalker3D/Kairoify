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
package icu.kairo.kairoify.ui.globe

import kotlin.math.cos
import kotlin.math.sin

/** Where a place lands on the globe's disc: x and y in -1..1 (y up), and whether it faces the viewer. */
data class Projected(val x: Float, val y: Float, val visible: Boolean)

/** Orthographic projection: the globe seen from far away, turned so ([centerLat], [centerLon]) faces the viewer. */
fun project(lat: Double, lon: Double, centerLat: Double, centerLon: Double): Projected {
    val phi = Math.toRadians(lat)
    val phi0 = Math.toRadians(centerLat)
    val dLon = Math.toRadians(lon - centerLon)
    val x = cos(phi) * sin(dLon)
    val y = cos(phi0) * sin(phi) - sin(phi0) * cos(phi) * cos(dLon)
    val facing = sin(phi0) * sin(phi) + cos(phi0) * cos(phi) * cos(dLon)
    return Projected(x.toFloat(), y.toFloat(), facing > 0)
}

/** Keeps longitude in -180..180. */
fun wrapLon(lon: Double): Double = ((lon + 540.0) % 360.0) - 180.0
