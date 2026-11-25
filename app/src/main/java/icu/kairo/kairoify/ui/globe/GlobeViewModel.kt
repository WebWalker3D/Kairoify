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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.source.radiobrowser.PlacedStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GlobeState(val loading: Boolean = true, val stations: List<PlacedStation> = emptyList(), val error: String? = null)

/** Loads the stations for the radio globe once. */
class GlobeViewModel(private val load: suspend () -> List<PlacedStation>) : ViewModel() {
    private val _state = MutableStateFlow(GlobeState())
    val state: StateFlow<GlobeState> = _state.asStateFlow()

    init {
        retry()
    }

    fun retry() {
        _state.value = GlobeState()
        viewModelScope.launch {
            _state.value = try {
                GlobeState(loading = false, stations = load())
            } catch (e: SourceException) {
                GlobeState(loading = false, error = e.message ?: "Couldn't reach Radio Browser.")
            }
        }
    }
}
