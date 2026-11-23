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

import icu.kairo.kairoify.core.ytdlp.EngineStatus
import icu.kairo.kairoify.core.ytdlp.UpdateResult
import org.junit.Test
import kotlin.test.assertEquals

class EngineSummaryTest {
    private val builtIn = EngineStatus("2026.10.26", updated = false, updateError = null)

    @Test
    fun `starting`() = assertEquals("Music engine: starting…", engineSummary(null, null))

    @Test
    fun `built in`() = assertEquals("Music engine: yt-dlp 2026.10.26 (built-in)", engineSummary(builtIn, null))

    @Test
    fun `updated`() = assertEquals(
        "Music engine: yt-dlp 2026.11.02 (updated)",
        engineSummary(EngineStatus("2026.11.02", updated = true, updateError = null), "2026.11.02"),
    )

    @Test
    fun `downloaded but not running yet`() = assertEquals(
        "Music engine: yt-dlp 2026.10.26 (built-in). Version 2026.11.02 is downloaded; restart Kairoify to use it.",
        engineSummary(builtIn, "2026.11.02"),
    )

    @Test
    fun `check results in plain words`() {
        assertEquals("Checked a moment ago. Try again later.", updateMessage(UpdateResult.NotDue))
        assertEquals("The music engine is up to date.", updateMessage(UpdateResult.UpToDate("2026.10.26")))
        assertEquals("Version 2026.11.02 downloaded. Restart Kairoify to use it.", updateMessage(UpdateResult.Downloaded("2026.11.02")))
        assertEquals("Couldn't check for an update.", updateMessage(UpdateResult.Failed("Couldn't check for an update.")))
    }
}
