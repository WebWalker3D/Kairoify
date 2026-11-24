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
package icu.kairo.kairoify.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import icu.kairo.kairoify.player.ResumeOffer
import kotlinx.coroutines.delay

/** How long "Start track over?" stays up after a track resumes. */
const val START_OVER_VISIBLE_MS = 5_000L

/**
 * Floats above the player for five seconds after a track picks up where it was left, then fades away. Tapping it
 * starts the track over.
 */
@Composable
fun StartOverPill(offer: ResumeOffer?, onStartOver: () -> Unit, onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(offer?.id) {
        if (offer == null) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        delay(START_OVER_VISIBLE_MS)
        visible = false
        delay(FADE_MS.toLong())
        onDismiss()
    }
    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible && offer != null,
            enter = fadeIn(tween(FADE_MS)) + slideInVertically(tween(FADE_MS)) { it / 2 },
            exit = fadeOut(tween(FADE_MS)) + slideOutVertically(tween(FADE_MS)) { it / 2 },
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    visible = false
                    onStartOver()
                },
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                text = { Text("Start track over?") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private const val FADE_MS = 400
