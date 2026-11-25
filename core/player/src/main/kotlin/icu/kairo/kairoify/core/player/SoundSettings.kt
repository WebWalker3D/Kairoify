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
package icu.kairo.kairoify.core.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log

/** The person's sound choices, shared by Settings and the playback service. */
class SoundSettings(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    /** Evens out loud and quiet tracks (a gentle compressor and limiter). */
    var evenVolume: Boolean
        get() = prefs.getBoolean(EVEN, false)
        set(value) = prefs.edit().putBoolean(EVEN, value).apply()

    /** The equalizer preset to use; -1 is off. */
    var equalizerPreset: Int
        get() = prefs.getInt(PRESET, -1)
        set(value) = prefs.edit().putInt(PRESET, value).apply()

    /** The presets this phone's equalizer offers, saved by the player the first time it plays. */
    var presetNames: List<String>
        get() = prefs.getString(PRESETS, null)?.split('\n')?.filter(String::isNotEmpty).orEmpty()
        set(value) = prefs.edit().putString(PRESETS, value.joinToString("\n")).apply()

    /** The player's audio session, for the phone's own sound panel. */
    var audioSession: Int
        get() = prefs.getInt(SESSION, 0)
        set(value) = prefs.edit().putInt(SESSION, value).apply()

    fun listen(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == EVEN || key == PRESET) onChange() }
            .also(prefs::registerOnSharedPreferenceChangeListener)

    fun stopListening(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val PREFS = "sound"
        const val EVEN = "even_volume"
        const val PRESET = "eq_preset"
        const val PRESETS = "eq_presets"
        const val SESSION = "audio_session"
    }
}

/** Applies [SoundSettings] to one audio session. Effects the phone doesn't support are skipped quietly. */
class AudioEffects(private val settings: SoundSettings) {
    private var session = 0
    private var equalizer: Equalizer? = null
    private var dynamics: DynamicsProcessing? = null

    fun attach(sessionId: Int) {
        if (sessionId == session || sessionId == 0) return
        release()
        session = sessionId
        settings.audioSession = sessionId
        apply()
    }

    fun apply() {
        if (session == 0) return
        val preset = settings.equalizerPreset
        equalizer = equalizer ?: tryOrNull { Equalizer(0, session) }?.also { eq ->
            settings.presetNames = (0 until eq.numberOfPresets).map { presetName(eq.getPresetName(it.toShort())) }
        }
        equalizer?.let { eq ->
            tryOrNull {
                if (preset in 0 until eq.numberOfPresets) eq.usePreset(preset.toShort())
                eq.enabled = preset >= 0
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (settings.evenVolume) {
                dynamics = dynamics ?: tryOrNull { evenVolume(session) }
                dynamics?.let { tryOrNull { it.enabled = true } }
            } else {
                dynamics?.let { tryOrNull { it.enabled = false } }
            }
        }
    }

    fun release() {
        tryOrNull { equalizer?.release() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tryOrNull { dynamics?.release() }
        equalizer = null
        dynamics = null
        session = 0
    }

    private fun evenVolume(session: Int): DynamicsProcessing? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
            false, 0, true, 1, false, 0, true,
        ).build()
        return DynamicsProcessing(0, session, config).apply {
            // One full-range band: quiet tracks come up, loud ones come down, peaks are caught by the limiter.
            setMbcBandAllChannelsTo(0, DynamicsProcessing.MbcBand(true, 20_000f, 10f, 400f, 3f, -28f, 6f, -90f, 1f, 0f, 8f))
            setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -2f, 0f))
        }
    }

    private inline fun <T> tryOrNull(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w("AudioEffects", "Sound effect not available: ${e.message}")
        null
    }
}

/** A preset's name as the phone reports it, without the NUL padding some phones leave in it (it breaks accessibility). */
fun presetName(raw: String): String = raw.replace("\u0000", "").trim()
