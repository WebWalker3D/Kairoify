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

import icu.kairo.kairoify.core.data.FavoritesStore
import icu.kairo.kairoify.core.data.KairoifyDatabase
import icu.kairo.kairoify.core.data.RoomFavoritesStore
import icu.kairo.kairoify.core.data.createDatabase
import android.content.Context
import icu.kairo.kairoify.core.network.createHttpClient
import icu.kairo.kairoify.core.network.kairoifyUserAgent
import icu.kairo.kairoify.core.player.PlayerDependencies
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.player.PlayerConnection
import icu.kairo.kairoify.source.radiobrowser.RadioBrowserServers
import icu.kairo.kairoify.source.radiobrowser.RadioBrowserSource
import okhttp3.OkHttpClient
import java.util.Locale

/** The app's object graph, built once per process. */
class AppContainer(context: Context) : PlayerDependencies {
    override val userAgent: String = kairoifyUserAgent(BuildConfig.VERSION_NAME)

    val httpClient: OkHttpClient = createHttpClient(userAgent, context.cacheDir.resolve("http"))

    override val sourceRegistry: SourceRegistry = SourceRegistry(
        listOf(
            RadioBrowserSource(
                client = httpClient,
                baseUrl = RadioBrowserServers(httpClient)::baseUrl,
                countryCode = { Locale.getDefault().country.takeIf { it.length == 2 } },
            ),
        ),
    )

    val playerConnection: PlayerConnection = PlayerConnection(context.applicationContext)

    val database: KairoifyDatabase = createDatabase(context)

    val favorites: FavoritesStore = RoomFavoritesStore(database)
}
