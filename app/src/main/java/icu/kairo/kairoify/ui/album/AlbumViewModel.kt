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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icu.kairo.kairoify.core.model.Album
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Albums and concerts open into their tracks; everything else plays. */
fun opensDetails(item: Item): Boolean = item is Album

sealed interface AlbumUiState {
    data object Loading : AlbumUiState

    data class Loaded(val album: Album, val tracks: List<Track>, val folders: List<Album> = emptyList()) : AlbumUiState

    data class Failed(val message: String) : AlbumUiState
}

class AlbumViewModel(private val registry: SourceRegistry, private val ref: MediaRef) : ViewModel() {
    private val _state = MutableStateFlow<AlbumUiState>(AlbumUiState.Loading)
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = AlbumUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val details = registry.details(ref)
                AlbumUiState.Loaded(details.album, details.tracks, details.folders)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceException) {
                AlbumUiState.Failed(e.message ?: "Couldn't open this one.")
            } catch (e: Exception) {
                AlbumUiState.Failed("Couldn't open this one.")
            }
        }
    }
}
