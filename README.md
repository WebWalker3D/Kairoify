# Kairoify

**The encyclopedia of free music. Open-source radio.**

- Project home: <https://github.com/WebWalker3D/Kairoify>
- Downloads: <https://github.com/WebWalker3D/Kairoify/releases>
- Bugs and ideas: <https://github.com/WebWalker3D/Kairoify/issues>

Kairoify puts every free source of music under one app: live radio from every
country, recorded live concerts, public-domain recordings, Creative Commons
catalogs, and the free tiers of mainstream services. Search them all at once,
browse the world by country, genre and era, and play it all through one queue
and one library.

- No accounts, no ads, no tracking.
- Every source can be switched on or off (coming with Settings).
- Everything runs on your device — no Kairoify server in between.

## Status

**Version 0.4.0 — DJ mixes, NTS Radio and Wikimedia Commons.**

- **Live radio:** browse and search ~50,000 stations from every country, and see
  what song is playing.
- **Songs:** one search across YouTube Music, SoundCloud, Bandcamp, Audius, Mixcloud,
  hearthis.at, NTS Radio's archive and Wikimedia Commons. Play any result, or start a
  radio from a YouTube Music song.
- **Concerts and archives:** Live Music Archive concerts, Great 78 Project records,
  netlabels and public-domain classical from the Internet Archive. Concerts open into
  their tracks, and Home shows concerts recorded on this day in past years.
- **Favorites:** tap ♡ on anything playing, or long-press any station, song or
  concert. The Library tab keeps one list with Play all and Shuffle. A queue of stations skips
  like the preset buttons on a car radio.
- **In the background:** playback continues with notification controls. Android
  Auto shows your Favorites too.

Platform: Android 8.0 and newer, on 64-bit phones.

## Install

1. Open the [latest release](https://github.com/WebWalker3D/Kairoify/releases/latest).
2. Download `kairoify-<version>-arm64-v8a.apk`. That's the one for phones; the
   `x86_64` APK is for emulators.
3. Open the file on your phone and allow installing from that app when Android asks.

Kairoify isn't on the Play Store. Each release lists the SHA-256 of its APKs and
of the signing certificate, so you can check what you installed:

```
Signing certificate SHA-256:
22:A5:67:F6:B2:90:29:82:EC:45:D2:14:3A:B0:CD:DB:5A:46:F5:78:8B:88:7C:C6:DE:4E:6F:A8:DA:A2:06:D4
```

Updates install over the top as long as they're signed with the same certificate.
Kairoify doesn't check for its own updates yet, so watch the
[releases page](https://github.com/WebWalker3D/Kairoify/releases).

## Build it yourself

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Needs JDK 21, the Android SDK and Python 3.14 on the `PATH` (for the embedded
Python's packages). The build makes one APK per CPU type: `arm64-v8a` for phones,
`x86_64` for emulators. Every push also builds debug APKs in GitHub Actions.

Signed release builds read their signing key from a properties file kept outside
the repo (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`):

```bash
./gradlew assembleRelease -Pkairoify.signing=/path/to/release.properties
```

Without it, release APKs come out unsigned.

## Android Auto

Kairoify shows up in Android Auto with your Favorites, "Live now in <your country>"
and "Most popular worldwide". Playing a favorite queues the whole list, so ⏭/⏮ step
through them like radio presets. Because it isn't installed from the Play Store, Android Auto only lists
it after you enable **Unknown sources** in Android Auto's developer settings (open
Android Auto settings, tap the version number ten times to unlock developer
settings).

## The music engine

YouTube Music, SoundCloud and Bandcamp are played through [yt-dlp](https://github.com/yt-dlp/yt-dlp),
running on your phone, with [ytmusicapi](https://github.com/sigma67/ytmusicapi) for
YouTube Music search. Services change often, so Kairoify checks yt-dlp's official
GitHub releases once a day and downloads a newer version when there is one. Each
download is checked against the release's published SHA-256 sums and used from the
next start. About shows which version is running and can switch back to the one
built into the app.

## Sources

| Source | What it brings | Status |
|---|---|---|
| Radio Browser | ~50,000 live radio stations, every country | ✓ Live |
| YouTube Music | The mainstream catalog | ✓ Live |
| SoundCloud | Indie, DJ sets, remixes | ✓ Live |
| Bandcamp | Independent artists | ✓ Live |
| Internet Archive | Live Music Archive concerts, Great 78 Project, netlabels, Free Music Archive, Old Time Radio, hip-hop mixtapes, public-domain classical | ✓ Live |
| Audius | Independent artists | ✓ Live |
| Mixcloud | DJ mixes and radio shows | ✓ Live |
| NTS Radio | Two live channels and ~90,000 archived shows | ✓ Live |
| hearthis.at | DJ sets, live recordings, tracks | ✓ Live |
| Wikimedia Commons | Classical, folk and historical recordings | ✓ Live |
| Radio Paradise | Ad-free, human-curated radio channels | ✓ Live |
| SomaFM | Ad-free, listener-supported radio channels | ✓ Live |
| Nightride FM | Synthwave, darksynth, chillsynth and more | ✓ Live |
| Openverse | Creative Commons audio from many sites | ✓ Live |
| Library of Congress | Historical recordings, including the National Jukebox | ✓ Live |
| Audiobooks & poetry | LibriVox and the Internet Archive's Audio Books & Poetry, by genre | ✓ Live |
| Podcasts | Any show in Apple's public directory, played from its own feed | ✓ Live |
| Wavlake | Independent artists, free to stream | ✓ Live |
| Jamendo | Creative Commons music | Off until its client ID is entered in Settings |
| Freesound | Experimental, ambient and musical recordings | Off until its API key is entered in Settings |
| Europeana | European cultural recordings | Off until its API key is entered in Settings |
| ccMixter | Creative Commons remixes and originals | Off by default (its files block plays from outside its site; switching it on sends its page as the Referer) |

**Free radio** (on any song or album with an artist) builds a station on the phone from
every switched-on music source, not just one service. It plays the song you picked at once,
looks up similar artists in [MusicBrainz](https://musicbrainz.org) and
[ListenBrainz](https://listenbrainz.org) (open, keyless), searches every music source for
them, and adds songs really credited to them as they're found, artists taking turns. The
player shows the station and its **Up next** list. No server involved.

Songs, episodes, mixes and audiobook chapters pick up where you left them when you play
them again (live radio doesn't). A **Start track over?** button shows for five seconds
whenever that happens.

Explore groups every source into six sections: Music, Radio, Audiobooks & poetry,
Podcasts, Historical recordings and Sounds. Each has its own page with its own search,
results as rows of cover art, and its genres or collections to browse. Search's All view
Podcasts, Historical recordings and Sounds browse by topic (True crime, National Jukebox,
Speeches, Birdsong, Rain…), with the first few topics shown as shelves.
uses the same rows: sources with nothing are hidden, and slow or failed ones share one line.

Explore's Countries, Genres and Concerts & archives lists each have a filter box: type
part of a name (accents and case don't matter; a country's two-letter code works too).

Every source has a switch in **Home → ⚙ Settings**, with a key field for the ones that
need a registered key.

### Jamendo

Jamendo's API needs a free client ID. Register an app at
[developer.jamendo.com](https://developer.jamendo.com) and enter it in Settings. Or
build one in with:

```bash
./gradlew assembleDebug -Pkairoify.jamendo.clientId=YOUR_CLIENT_ID
# or: KAIROIFY_JAMENDO_CLIENT_ID=YOUR_CLIENT_ID ./gradlew assembleDebug
```

The ID is never stored in the repo. Without one, Jamendo stays off. Freesound and
Europeana work the same way, with keys entered in Settings.

More to follow — the goal is every free source there is. [`AUDIO.md`](AUDIO.md) lists
every current and planned source, with a dated change log.

## What's next

- **More sources:** dozens more are listed in [`AUDIO.md`](AUDIO.md), from federated
  catalogs (Funkwhale, Mirlo, Faircamp) to historical archives (UCSB cylinders, Gallica)
  and more public-domain collections.
- **Songs that never dead-end:** when a song fails on one service, find the same
  song on another and play that instead.
- **Save the song on the radio:** turn what a station is playing into a song you can
  favorite and play on demand.
- **Playlist import** from other music services.
- **Local music files** alongside everything streamed.
- **Discovery** shaped by what you listen to, kept on your phone.
- **Explore:** an interactive world radio globe, and browsing by decade and by
  source.
- **Listening:** synchronized lyrics, an equalizer and volume normalization.
- **Android TV.**
- **Podcasts and public-domain audiobooks.**
- **Library:** playlists, history, and saved albums and concerts, plus export and
  import of the whole library.
- **Downloads** for offline listening, wherever the source allows it.
- **Settings:** switch sources on or off, audio quality for Wi-Fi and mobile data,
  a sleep timer, and an in-app check for new Kairoify releases.
- **Search:** artist, album and playlist results, a "Top results" strip, and source
  filters.
- **Licenses:** an open-source notices screen, and artist and license shown for
  Creative Commons tracks.
- **Releases:** signed release builds published automatically by GitHub Actions.
- **Streaming protocols.** These aren't single services; each one opens up many sources at once:
  - **Icecast directory:** More internet radio stations.
  - **RSS audio enclosures:** Music podcasts, artist releases, DJ shows.
  - **Podcasting 2.0:** Independently distributed music, with optional artist tipping.
  - **Canimus:** Artist-owned music catalogs.
  - **ActivityPub:** Federated music distribution.
  - **OpenSubsonic:** Compatible music servers.
  - **Ampache API:** Community and personal music collections.
  - **PeerTube API:** Music videos and concerts (audio-only in Kairoify unless the owner decides otherwise).
  - **Owncast:** Independent live performances.
  - **M3U / M3U8:** Authorized radio and media playlists.
  - **PLS / XSPF:** More playlist formats.
  - **Direct HTTP audio:** Artist-published MP3, FLAC, AAC and Ogg streams.
  - **WebDAV:** Music collections you authorize on a remote server.
  - **UPnP / DLNA:** Music you authorize on your local network.
- **Further out:** an optional Kairoify server for sync, and Kairo, the companion
  AI, as a DJ.

## License

Kairoify is licensed under the **Common Public Attribution License Version 1.0
(CPAL-1.0)** — see [`LICENSE`](LICENSE). Works built on Kairoify must display the
attribution described in [`NOTICE.md`](NOTICE.md).

The Kairoify name and logo are not licensed. Forks must use their own name and
logo (see [`NOTICE.md`](NOTICE.md)).

Music played through Kairoify belongs to its artists and is subject to the terms of
the service it comes from. When Creative Commons sources arrive, their tracks will be
shown with their artist and license.
