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
package icu.kairo.kairoify.ui.album

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.testing.FakeSource
import icu.kairo.kairoify.testing.album
import icu.kairo.kairoify.testing.station
import icu.kairo.kairoify.testing.track
import icu.kairo.kairoify.testing.withMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `loads the album and its tracks`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val source = FakeSource("archive", albumTracks = listOf(track("1", "archive"), track("2", "archive")))
            val vm = AlbumViewModel(SourceRegistry(listOf(source)), MediaRef("archive", "x"))
            assertIs<AlbumUiState.Loading>(vm.state.value)
            advanceUntilIdle()
            val loaded = assertIs<AlbumUiState.Loaded>(vm.state.value)
            assertEquals("Album x", loaded.album.title)
            assertEquals(2, loaded.tracks.size)
        }
    }

    @Test
    fun `failures say why and can be retried`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val source = FakeSource("archive", detailsError = SourceException.Unavailable("Couldn't reach Internet Archive."))
            val vm = AlbumViewModel(SourceRegistry(listOf(source)), MediaRef("archive", "x"))
            advanceUntilIdle()
            assertEquals(AlbumUiState.Failed("Couldn't reach Internet Archive."), vm.state.value)
            source.detailsError = null
            vm.retry()
            advanceUntilIdle()
            assertIs<AlbumUiState.Loaded>(vm.state.value)
            assertEquals(2, source.detailsCalls)
        }
    }

    @Test
    fun `only albums open a details page`() {
        assertTrue(opensDetails(album("x")))
        assertFalse(opensDetails(track("1")))
        assertFalse(opensDetails(station("a")))
    }
}
