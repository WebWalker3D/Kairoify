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
package icu.kairo.kairoify.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Kairoify's own palette: an amber "radio dial" glow on near-black. */
object KairoifyColors {
    val Background = Color(0xFF0F1014)
    val Surface = Color(0xFF181A20)
    val SurfaceVariant = Color(0xFF23262E)
    val Amber = Color(0xFFFFB547)
    val OnAmber = Color(0xFF1A1200)
    val Teal = Color(0xFF5EC8D8)
    val OnSurface = Color(0xFFECEDEF)
    val OnSurfaceVariant = Color(0xFFA3A7B0)
    val Live = Color(0xFFFF5467)
    val AmberContainer = Color(0xFF3A2E14)
}

val DarkColors = darkColorScheme(
    primary = KairoifyColors.Amber,
    onPrimary = KairoifyColors.OnAmber,
    secondary = KairoifyColors.Teal,
    secondaryContainer = KairoifyColors.AmberContainer,
    onSecondaryContainer = KairoifyColors.Amber,
    background = KairoifyColors.Background,
    onBackground = KairoifyColors.OnSurface,
    surface = KairoifyColors.Surface,
    onSurface = KairoifyColors.OnSurface,
    surfaceVariant = KairoifyColors.SurfaceVariant,
    onSurfaceVariant = KairoifyColors.OnSurfaceVariant,
    surfaceContainer = KairoifyColors.Surface,
    error = KairoifyColors.Live,
)

val LightColors = lightColorScheme(
    primary = Color(0xFFA35F00),
    onPrimary = Color.White,
    secondary = Color(0xFF1E7F8C),
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF17181C),
    surface = Color.White,
    onSurface = Color(0xFF17181C),
    error = Color(0xFFC8102E),
)
