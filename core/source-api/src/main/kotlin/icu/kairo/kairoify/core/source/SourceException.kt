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
package icu.kairo.kairoify.core.source

/** Why a source failed, so the UI can say so in plain words. */
sealed class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unavailable(message: String, cause: Throwable? = null) : SourceException(message, cause)
    class RateLimited(message: String) : SourceException(message)
    class NotFound(message: String) : SourceException(message)
    class NeedsUpdate(message: String, cause: Throwable? = null) : SourceException(message, cause)
    class Geoblocked(message: String) : SourceException(message)

    /** This one item can't be played (age-restricted, subscription-only, or a failure nobody classified); others still can. */
    class Unplayable(message: String, cause: Throwable? = null) : SourceException(message, cause)
}
