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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.testing.track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ItemRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `long press opens actions`() {
        val clicked = mutableListOf<Item>()
        val longPressed = mutableListOf<Item>()
        val song = track("1")
        compose.setContent { ItemRow(song, onClick = { clicked += it }, onLongClick = { longPressed += it }) }
        compose.onNodeWithText("Song 1").performTouchInput { longClick() }
        assertEquals(listOf<Item>(song), longPressed)
        assertEquals(emptyList(), clicked)
    }

    @Test
    fun `songs show their duration`() {
        compose.setContent { ItemRow(track("1").copy(durationMillis = 245_000), onClick = {}) }
        compose.onNodeWithText("4:05").assertIsDisplayed()
    }

    @Test
    fun `badge names the source`() {
        compose.setContent { ItemRow(track("1"), onClick = {}, badge = "SoundCloud") }
        compose.onNodeWithText("SoundCloud").assertIsDisplayed()
    }
}
