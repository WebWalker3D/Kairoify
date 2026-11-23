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
package icu.kairo.kairoify.core.ytdlp

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/** Calls functions in kairoify_engine.py through Chaquopy, starting Python the first time it's needed. */
class ChaquopyCall(context: Context) : PythonCall {
    private val appContext = context.applicationContext

    private val module: PyObject by lazy {
        synchronized(ChaquopyCall::class.java) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(appContext))
        }
        Python.getInstance().getModule("kairoify_engine")
    }

    override fun call(function: String, vararg args: Any?): String = module.callAttr(function, *args).toString()
}
