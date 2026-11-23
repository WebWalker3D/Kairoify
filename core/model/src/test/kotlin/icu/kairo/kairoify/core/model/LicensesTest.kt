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
package icu.kairo.kairoify.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LicensesTest {
    @Test
    fun `creative commons urls become short names`() {
        assertEquals("CC BY 4.0", licenseFromUrl("https://creativecommons.org/licenses/by/4.0/")?.name)
        assertEquals("CC BY-SA 3.0", licenseFromUrl("http://creativecommons.org/licenses/by-sa/3.0/")?.name)
        assertEquals("CC BY-NC-ND 2.5", licenseFromUrl("https://creativecommons.org/licenses/by-nc-nd/2.5/us/")?.name)
        assertEquals("https://creativecommons.org/licenses/by/4.0/", licenseFromUrl("https://creativecommons.org/licenses/by/4.0/")?.url)
    }

    @Test
    fun `public domain urls`() {
        assertEquals("CC0", licenseFromUrl("http://creativecommons.org/publicdomain/zero/1.0/")?.name)
        assertEquals("Public domain", licenseFromUrl("https://creativecommons.org/publicdomain/mark/1.0/")?.name)
    }

    @Test
    fun `anything else is no license`() {
        assertNull(licenseFromUrl(null))
        assertNull(licenseFromUrl(""))
        assertNull(licenseFromUrl("https://example.com/terms"))
    }
}
