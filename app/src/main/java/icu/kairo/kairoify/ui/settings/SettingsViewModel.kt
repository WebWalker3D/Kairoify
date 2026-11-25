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
package icu.kairo.kairoify.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icu.kairo.kairoify.settings.SourceCatalog
import icu.kairo.kairoify.settings.SourceSettings
import icu.kairo.kairoify.source.protocols.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SourceRow(
    val id: String,
    val name: String,
    val note: String,
    val switchedOn: Boolean,
    /** On and usable: switched on, and holding a key if it needs one. */
    val active: Boolean,
    val keyName: String?,
    val key: String,
    val status: String,
)

/** Where adding a server stands: idle, checking it, or the reason it didn't answer. */
sealed interface ServerCheck {
    data object Idle : ServerCheck
    data object Checking : ServerCheck
    data class Failed(val message: String) : ServerCheck
}

class SettingsViewModel(
    private val catalog: SourceCatalog,
    private val settings: SourceSettings,
    private val checkServer: suspend (Server) -> String? = { null },
) : ViewModel() {
    val rows: StateFlow<List<SourceRow>> = settings.changes.map { rows() }.stateIn(viewModelScope, SharingStarted.Eagerly, rows())

    val links: StateFlow<List<String>> = settings.changes.map { settings.links }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.links)

    val servers: StateFlow<List<Server>> = settings.changes.map { settings.servers }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.servers)

    private val _check = MutableStateFlow<ServerCheck>(ServerCheck.Idle)
    val check: StateFlow<ServerCheck> = _check.asStateFlow()

    fun addLink(url: String) = settings.addLink(url)

    fun removeLink(url: String) = settings.removeLink(url)

    fun removeServer(id: String) = settings.removeServer(id)

    /** Checks [server] answers and signs in, then saves it; [onSaved] runs only when it did. */
    fun addServer(server: Server, onSaved: () -> Unit) {
        _check.value = ServerCheck.Checking
        viewModelScope.launch {
            val problem = checkServer(server)
            if (problem == null) {
                settings.saveServer(server)
                _check.value = ServerCheck.Idle
                onSaved()
            } else {
                _check.value = ServerCheck.Failed(problem)
            }
        }
    }

    fun clearCheck() {
        _check.value = ServerCheck.Idle
    }

    fun setOn(id: String, on: Boolean) = settings.setOn(id, on)

    fun saveKey(name: String, value: String) = settings.setKey(name, value)

    private fun rows(): List<SourceRow> = catalog.entries.map { entry ->
        val on = catalog.isSwitchedOn(entry)
        val key = entry.keyName?.let(catalog::key).orEmpty()
        val active = catalog.isEnabled(entry.source)
        SourceRow(
            id = entry.source.info.id,
            name = entry.source.info.displayName,
            note = entry.note,
            switchedOn = on,
            active = active,
            keyName = entry.keyName,
            key = key,
            status = when {
                active -> "On"
                on && entry.keyName != null -> "Add its key to switch it on."
                else -> "Off"
            },
        )
    }
}
