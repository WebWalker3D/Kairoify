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
package icu.kairo.kairoify

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import icu.kairo.kairoify.core.player.PlayerDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KairoifyApp : Application(), PlayerDependencies.Provider, SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    /** The attribution splash shows once per process start. */
    var attributionShown = false

    override val playerDependencies: PlayerDependencies get() = container

    override fun onCreate() {
        super.onCreate()
        // Start Python and import yt-dlp now, so the first song search doesn't pay for it.
        container.appScope.launch(Dispatchers.IO) { runCatching { container.engine.start() } }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })) }
            .crossfade(true)
            .build()
}
