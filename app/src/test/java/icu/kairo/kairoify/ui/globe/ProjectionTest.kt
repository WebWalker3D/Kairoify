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

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectionTest {
    @Test
    fun `the place in the middle lands in the middle`() {
        val p = project(48.0, 2.0, 48.0, 2.0)
        assertEquals(0f, p.x, 1e-4f)
        assertEquals(0f, p.y, 1e-4f)
        assertTrue(p.visible)
    }

    @Test
    fun `east is right, north is up, the far side is hidden`() {
        assertTrue(project(0.0, 30.0, 0.0, 0.0).x > 0)
        assertTrue(project(30.0, 0.0, 0.0, 0.0).y > 0)
        assertFalse(project(0.0, 180.0, 0.0, 0.0).visible)
    }

    @Test
    fun `longitude wraps`() {
        assertEquals(-170.0, wrapLon(190.0), 1e-9)
        assertEquals(170.0, wrapLon(-190.0), 1e-9)
    }
}
