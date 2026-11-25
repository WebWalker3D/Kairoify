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
import icu.kairo.kairoify.core.data.HistoryStore
import icu.kairo.kairoify.core.data.PlaylistStore
import icu.kairo.kairoify.core.data.RoomHistoryStore
import icu.kairo.kairoify.core.data.RoomPlaylistStore
import icu.kairo.kairoify.core.data.createDatabase
import icu.kairo.kairoify.core.model.Item
import kotlinx.coroutines.flow.first
import android.content.Context
import icu.kairo.kairoify.player.FreeRadio
import icu.kairo.kairoify.core.source.withTopics
import icu.kairo.kairoify.settings.BrowseTopics
import icu.kairo.kairoify.local.LocalMusicSource
import icu.kairo.kairoify.source.lyrics.LrcLib
import icu.kairo.kairoify.importing.PlaylistImporter
import icu.kairo.kairoify.core.source.findSameSong
import icu.kairo.kairoify.source.listenbrainz.SimilarArtists
import icu.kairo.kairoify.player.ResumePositions
import icu.kairo.kairoify.source.radioparadise.RadioParadiseSource
import icu.kairo.kairoify.source.nightride.NightrideSource
import icu.kairo.kairoify.source.wavlake.WavlakeSource
import icu.kairo.kairoify.source.podcasts.PodcastSource
import icu.kairo.kairoify.source.europeana.EuropeanaSource
import icu.kairo.kairoify.source.freesound.FreesoundSource
import icu.kairo.kairoify.source.loc.LocSource
import icu.kairo.kairoify.source.openverse.OpenverseSource
import icu.kairo.kairoify.source.somafm.SomaFmSource
import icu.kairo.kairoify.source.ccmixter.CcMixterSource
import icu.kairo.kairoify.settings.SourceSettings
import icu.kairo.kairoify.settings.SourceEntry
import icu.kairo.kairoify.settings.SourceCatalog
import icu.kairo.kairoify.settings.SourceCategory
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

    /** Source switches and API keys the person set in Settings. */
    val sourceSettings = SourceSettings(context.getSharedPreferences("sources", Context.MODE_PRIVATE))

    /**
     * Every source, its default switch, and what the person should know about it. Authorization notes match AUDIO.md:
     * "official" sources play through the provider's own API; "not authorized" ones play by extraction (yt-dlp),
     * included by the owner's choice; keyed ones stay off until a key is entered.
     */
    /** Music files on the phone, shown with everything streamed. */
    val localMusic = LocalMusicSource(context)

    /** Synced lyrics, looked up only for the song playing when the person opens them. */
    val lyrics = LrcLib(httpClient)

    /** Playlists from elsewhere: yt-dlp lists the link, each song is found on the free sources. */
    val importer by lazy { PlaylistImporter(listLink = engine::flatSearch, findSong = { sourceRegistry.findSameSong(it, skipSource = "") }) }

    val sourceCatalog: SourceCatalog by lazy {
        SourceCatalog(
            listOf(
                SourceEntry(
                    RadioBrowserSource(
                        client = httpClient,
                        baseUrl = RadioBrowserServers(httpClient)::baseUrl,
                        countryCode = { Locale.getDefault().country.takeIf { it.length == 2 } },
                    ),
                    defaultOn = true,
                    note = "Live radio from every country. Official API.",
                category = SourceCategory.RADIO),
                SourceEntry(YouTubeMusicSource(engine), true, note = "Plays through yt-dlp, which YouTube's terms don't authorize.", category = SourceCategory.MUSIC),
                SourceEntry(SoundCloudSource(engine), true, note = "Plays through yt-dlp, which SoundCloud's terms don't authorize.", category = SourceCategory.MUSIC),
                SourceEntry(BandcampSource(BandcampSearchApi(httpClient), engine), true, note = "Plays through yt-dlp, which Bandcamp's terms don't authorize.", category = SourceCategory.MUSIC),
                SourceEntry(MixcloudSource(MixcloudSearchApi(httpClient), engine), true, note = "Plays through yt-dlp; Mixcloud excludes audio from its API.", category = SourceCategory.MUSIC),
                SourceEntry(NtsSource(NtsApi(httpClient), engine), true, note = "Live channels are official; archived shows play from SoundCloud or Mixcloud.", category = SourceCategory.RADIO),
                SourceEntry(ArchiveSource(httpClient), true, note = "Concerts, 78s, netlabels, classical. Official API.", category = SourceCategory.MUSIC),
                SourceEntry(AudiusSource(httpClient), true, note = "Independent artists. Official API.", category = SourceCategory.MUSIC),
                SourceEntry(HearthisSource(httpClient), true, note = "DJ sets. Public API; developer terms not yet confirmed.", category = SourceCategory.MUSIC),
                SourceEntry(WikimediaSource(httpClient).withTopics(BrowseTopics.wikimedia), true, note = "Freely licensed recordings. Official API.", category = SourceCategory.HISTORY),
                SourceEntry(
                    JamendoSource(httpClient, { sourceCatalog.key("jamendo") }),
                    defaultOn = true,
                    keyName = "jamendo",
                    keyFallback = BuildConfig.JAMENDO_CLIENT_ID,
                    note = "Creative Commons music. Needs a free client ID from developer.jamendo.com.",
                category = SourceCategory.MUSIC),
                SourceEntry(RadioParadiseSource(httpClient), true, note = "Listener-supported, ad-free, human-curated radio. Public channel list and streams.", category = SourceCategory.RADIO),
                SourceEntry(SomaFmSource(httpClient), true, note = "Ad-free listener-supported radio. Public channel list and streams.", category = SourceCategory.RADIO),
                SourceEntry(localMusic, true, note = "Music files on this phone. Asks once for permission to read audio.", category = SourceCategory.MUSIC),
                SourceEntry(NightrideSource(), true, note = "Free listener-supported synthwave radio: nine stations. Public streams.", category = SourceCategory.RADIO),
                SourceEntry(OpenverseSource(httpClient).withTopics(BrowseTopics.sounds), true, note = "Creative Commons audio from many sites. Official API.", category = SourceCategory.SOUNDS),
                SourceEntry(LocSource(httpClient).withTopics(BrowseTopics.libraryOfCongress, shelves = 0), true, note = "Library of Congress recordings, including the National Jukebox. Official API.", category = SourceCategory.HISTORY),
                SourceEntry(PodcastSource(httpClient).withTopics(BrowseTopics.podcasts), true, note = "Shows from Apple's public podcast directory; episodes play from each show's own feed.", category = SourceCategory.PODCASTS),
                SourceEntry(WavlakeSource(httpClient), true, note = "Independent artists who publish free to stream, with optional tips. Public API.", category = SourceCategory.MUSIC),
                SourceEntry(ArchiveSource.audiobooks(httpClient), true, note = "LibriVox and the Internet Archive's Audio Books & Poetry collection, browsable by genre.", category = SourceCategory.AUDIOBOOKS),
                SourceEntry(
                    FreesoundSource(httpClient, { sourceCatalog.key("freesound") }).withTopics(BrowseTopics.sounds),
                    defaultOn = true,
                    keyName = "freesound",
                    note = "Experimental and ambient sounds. Needs a free API key from freesound.org/apiv2/apply.",
                category = SourceCategory.SOUNDS),
                SourceEntry(
                    EuropeanaSource(httpClient, { sourceCatalog.key("europeana") }).withTopics(BrowseTopics.europeana),
                    defaultOn = true,
                    keyName = "europeana",
                    note = "European cultural recordings. Needs a free API key from pro.europeana.eu.",
                category = SourceCategory.HISTORY),
                SourceEntry(
                    CcMixterSource(httpClient),
                    defaultOn = false,
                    note = "Creative Commons remixes. Its files only play for requests that say they come from ccMixter's own pages (hotlink protection); switching this on sends that header.",
                category = SourceCategory.MUSIC),
            ),
            sourceSettings,
        )
    }

    /** "Free radio": stations mixed on the phone from every music source, with similar artists from ListenBrainz. */
    val freeRadio: FreeRadio by lazy {
        FreeRadio(
            sourceRegistry,
            similar = SimilarArtists(httpClient)::similarTo,
            isMusic = { sourceCatalog.inCategory(it, SourceCategory.MUSIC) },
        )
    }

    override val sourceRegistry: SourceRegistry by lazy {
        SourceRegistry(sourceCatalog.entries.map { it.source }, isEnabled = sourceCatalog::isEnabled)
    }

    val playerConnection: PlayerConnection = PlayerConnection(
        context.applicationContext,
        ResumePositions(context.getSharedPreferences("resume", Context.MODE_PRIVATE)),
    )

    val database: KairoifyDatabase = createDatabase(context)

    val favorites: FavoritesStore = RoomFavoritesStore(database)

    val history: HistoryStore = RoomHistoryStore(database)

    val playlists: PlaylistStore = RoomPlaylistStore(database)

    override suspend fun favoriteItems(): List<Item> = favorites.favorites.first().map { it.item }
}
