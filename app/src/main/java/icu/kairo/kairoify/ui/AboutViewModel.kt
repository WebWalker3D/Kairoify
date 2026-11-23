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
package icu.kairo.kairoify.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icu.kairo.kairoify.core.ytdlp.EngineStatus
import icu.kairo.kairoify.core.ytdlp.EngineUpdater
import icu.kairo.kairoify.core.ytdlp.UpdateResult
import icu.kairo.kairoify.core.ytdlp.YtDlpEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The "Music engine" line in About. */
fun engineSummary(status: EngineStatus?, activeVersion: String?): String {
    if (status == null) return "Music engine: starting…"
    val running = "Music engine: yt-dlp ${status.version} (${if (status.updated) "updated" else "built-in"})"
    return if (activeVersion != null && activeVersion != status.version) {
        "$running. Version $activeVersion is downloaded; restart Kairoify to use it."
    } else {
        running
    }
}

fun updateMessage(result: UpdateResult): String = when (result) {
    UpdateResult.NotDue -> "Checked a moment ago. Try again later."
    is UpdateResult.UpToDate -> "The music engine is up to date."
    is UpdateResult.Downloaded -> "Version ${result.version} downloaded. Restart Kairoify to use it."
    is UpdateResult.Failed -> result.message
}

data class EngineUiState(val summary: String, val canUseBuiltIn: Boolean, val message: String? = null, val checking: Boolean = false)

class AboutViewModel(private val engine: YtDlpEngine, private val updater: EngineUpdater) : ViewModel() {
    private val _state = MutableStateFlow(current())
    val state: StateFlow<EngineUiState> = _state.asStateFlow()

    fun checkForUpdate() {
        _state.value = current().copy(checking = true)
        viewModelScope.launch {
            val result = updater.check(force = true)
            _state.value = current().copy(message = updateMessage(result))
        }
    }

    fun useBuiltIn() {
        updater.rollback()
        _state.value = current().copy(message = "Restart Kairoify to switch back.")
    }

    private fun current() = EngineUiState(
        summary = engineSummary(engine.currentStatus, updater.activeVersion()),
        canUseBuiltIn = updater.activeVersion() != null || engine.currentStatus?.updated == true,
    )
}
