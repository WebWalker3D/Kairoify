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

class SettingsViewModel(private val catalog: SourceCatalog, private val settings: SourceSettings) : ViewModel() {
    val rows: StateFlow<List<SourceRow>> = settings.changes.map { rows() }.stateIn(viewModelScope, SharingStarted.Eagerly, rows())

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
