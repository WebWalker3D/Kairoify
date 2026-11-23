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
import icu.kairo.kairoify.core.model.Item
import kotlinx.coroutines.flow.first
import android.content.Context
import icu.kairo.kairoify.core.ytdlp.ChaquopyCall
import icu.kairo.kairoify.core.ytdlp.EngineUpdater
import kotlinx.coroutines.launch
import icu.kairo.kairoify.core.ytdlp.YtDlpEngine
import icu.kairo.kairoify.source.archive.ArchiveSource
import icu.kairo.kairoify.source.audius.AudiusSource
import icu.kairo.kairoify.source.jamendo.JamendoSource
import icu.kairo.kairoify.source.hearthis.HearthisSource
import icu.kairo.kairoify.source.wikimedia.WikimediaSource
import icu.kairo.kairoify.source.ytdlp.BandcampSearchApi
import icu.kairo.kairoify.source.ytdlp.MixcloudSearchApi
import icu.kairo.kairoify.source.ytdlp.MixcloudSource
import icu.kairo.kairoify.source.ytdlp.NtsApi
import icu.kairo.kairoify.source.ytdlp.NtsSource
import icu.kairo.kairoify.source.ytdlp.BandcampSource
import icu.kairo.kairoify.source.ytdlp.SoundCloudSource
import icu.kairo.kairoify.source.ytdlp.YouTubeMusicSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
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

    /** Lives as long as the process: engine warm-up and other background work that outlasts any screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** yt-dlp + ytmusicapi in the app's own Python, shared by the YouTube Music, SoundCloud and Bandcamp sources. */
    /** Fetches newer yt-dlp releases; a downloaded one is loaded the next time the engine starts. */
    val updater = EngineUpdater(httpClient, context.filesDir.resolve("engine-updates"))

    val engine = YtDlpEngine(
        python = ChaquopyCall(context),
        cacheDir = context.cacheDir.resolve("engine"),
        qjsPath = File(context.applicationInfo.nativeLibraryDir, "libqjs.so").path,
        updateZip = updater::updateToLoad,
        onNeedsUpdate = { appScope.launch { updater.check(force = true) } },
        onUpdateFailed = { updater.activeVersion()?.let(updater::markBad) },
    )

    override val sourceRegistry: SourceRegistry = SourceRegistry(
        listOf(
            RadioBrowserSource(
                client = httpClient,
                baseUrl = RadioBrowserServers(httpClient)::baseUrl,
                countryCode = { Locale.getDefault().country.takeIf { it.length == 2 } },
            ),
            YouTubeMusicSource(engine),
            SoundCloudSource(engine),
            BandcampSource(BandcampSearchApi(httpClient), engine),
            ArchiveSource(httpClient),
            AudiusSource(httpClient),
            // Not authorized by Mixcloud (extraction), included by the owner's choice like YouTube/SoundCloud/Bandcamp.
            MixcloudSource(MixcloudSearchApi(httpClient), engine),
            NtsSource(NtsApi(httpClient), engine),
            HearthisSource(httpClient),
            // ccMixter is built but off: its MP3s answer 403 unless the request claims to come from ccMixter's own
            // page (hotlink protection). Whether to send that Referer is the owner's call.
            WikimediaSource(httpClient),
        ) + listOfNotNull(
            // Switched off until a registered client ID is built in.
            BuildConfig.JAMENDO_CLIENT_ID.takeIf { it.isNotBlank() }?.let { JamendoSource(httpClient, it) },
        ),
    )

    val playerConnection: PlayerConnection = PlayerConnection(context.applicationContext)

    val database: KairoifyDatabase = createDatabase(context)

    val favorites: FavoritesStore = RoomFavoritesStore(database)

    override suspend fun favoriteItems(): List<Item> = favorites.favorites.first().map { it.item }
}
