# Kairoify

**The encyclopedia of free music. Open-source radio.**

- Project home: <https://github.com/WebWalker3D/Kairoify>
- Downloads: <https://github.com/WebWalker3D/Kairoify/releases>
- Bugs and ideas: <https://github.com/WebWalker3D/Kairoify/issues>

Kairoify puts every free source of music under one app: live radio from every
country, recorded live concerts, public-domain recordings, Creative Commons
catalogs, the free tiers of mainstream services, and your own music, on the phone,
on your servers and on your home network. Search them all at once, browse the world
by country, genre and era, and play it all through one queue and one library.
Kairoify plays audio: music, radio, podcasts, audiobooks. It doesn't play video.

- No accounts, no ads, no tracking.
- Every source can be switched on or off in Settings.
- Everything runs on your device — there is no Kairoify server, and none is planned.
  The phone talks to each source directly.

## Status

**Version 0.5.0 — playlists, lyrics, captions and the radio globe.**

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
- **Playlists and history:** make your own playlists (long-press anything, or the
  player's Add to playlist), reorder and rename them. Library's History tab lists
  everything you've played, with play counts. Both stay on your phone.
- **Songs that don't dead-end:** when a song won't play on its service, Kairoify looks
  for the same recording (same artist and title, similar length, not a remix) on your
  other music sources and plays that instead.
- **Save the song on the radio:** when a station names the song it's playing, the
  player's "Save this song" finds it on your music sources and keeps it in Favorites.
- **Music on your phone:** allow access once and your own music files show up in
  search, on Home and in Library's "On this phone" tab, alongside everything streamed.
- **Sound:** Settings has Even out volume, the phone's equalizer presets, and a link
  to the phone's own sound panel.
- **Lyrics:** tap Lyrics in the player for words that follow the song (from LRCLIB's
  open database); tap a line to jump there.
- **Captions:** speeches, talks and audiobooks get a Captions button that follows along the same
  way, from the subtitles Wikimedia Commons and the Internet Archive publish (the Archive's
  automatic ones are marked as automatic). When a recording has none, **Transcribe on this phone**
  writes them with [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT), running entirely on
  the phone and keeping just ahead of what's playing. The first time, it downloads a 60 MB speech
  model (checked against its pinned SHA-256); nothing is sent anywhere. Any language.
- **For you:** Home opens with Recently played and "Because you play …", built on
  the phone from your History.
- **Import playlists:** paste a YouTube, YouTube Music, SoundCloud or Bandcamp link, or
  any "Artist - Title" list (e.g. copied from Spotify), and each song is found on the
  free sources.
- **Radio globe:** Explore › Radio globe shows 3,000 live stations as dots on a
  globe you spin with a finger; tap one to listen.
- **In the background:** playback continues with notification controls. Android
  Auto shows your Favorites too.

**On `main` since 0.5.0** (in the next release):

- **Links:** paste any web address into search: an audio file (MP3, FLAC, AAC, Ogg,
  Opus…), a live stream, an M3U/M3U8, PLS or XSPF playlist, an RSS or Atom feed (podcasts,
  artist releases, DJ shows, with Podcasting 2.0 music feeds and tip support marked), or a web
  page that advertises a feed. Save links in Settings to keep them on Home.
- **Your servers:** add a Subsonic, OpenSubsonic (Navidrome, Gonic, LMS, Airsonic…) or
  Ampache server, or a WebDAV folder (Nextcloud, ownCloud, a NAS). Their music is searched
  with everything else, folders open like albums, and new albums show on Home.
- **Your home network:** UPnP/DLNA media servers on your Wi-Fi (Plex, Jellyfin, MiniDLNA,
  many NAS boxes) are found automatically and browsed folder by folder.
- **Icecast directory:** every station listed at dir.xiph.org, searchable from Radio
  (off by default: its list is a 9 MB download, fetched at most once a week).
- **Internet Archive:** uploads without a "creator" now get their artist from the MP3 tags or an
  "Artist - Album" title, so Free radio and lyrics work for them.

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

Needs JDK 21, the Android SDK (with the NDK and CMake 4.1.2, for whisper.cpp) and Python 3.14 on the `PATH` (for the embedded
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
| Links | Any audio file, stream, M3U/PLS/XSPF playlist or RSS/Atom feed you paste or save | ✓ On `main` |
| Your music servers | Subsonic, OpenSubsonic and Ampache servers you add | ✓ On `main` |
| Your WebDAV folders | Nextcloud, ownCloud, NAS or any WebDAV share you add | ✓ On `main` |
| Media servers on your network | UPnP/DLNA servers on your Wi-Fi, found automatically | ✓ On `main` |
| Icecast directory | ~13,000 stations listed at dir.xiph.org | On `main`, off by default (9 MB weekly download) |

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
results as rows of cover art, and its genres or collections to browse. Podcasts,
Historical recordings and Sounds browse by topic (True crime, National Jukebox, Songs of
America, Speeches, Birdsong, Rain…), with the first few topics shown as shelves. Search's
All view uses the same rows: sources with nothing are hidden, and slow or failed ones share
one line.

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

### Your own servers, links and network

The phone connects to each one directly; nothing passes through a Kairoify server or any other go-between.

- **Subsonic / OpenSubsonic / Ampache:** Settings › Your servers › Add a server. Use the
  address you open the server at. Kairoify signs in with the Subsonic API's salted token, so
  the password itself is never sent. Ampache works through its Subsonic-compatible API.
- **WebDAV:** the folder's WebDAV address, e.g.
  `https://cloud.example.com/remote.php/dav/files/you/Music` for Nextcloud. Folders laid out
  as `Artist/Album/03 Song.mp3` give songs their artist. Search looks through up to 400
  folders, once per run of the app.
- Each server is checked before it's saved. Passwords are kept only in the app's own
  storage and are left out of Android's cloud backups and device transfers.
- **UPnP/DLNA:** nothing to set up; servers on the same Wi-Fi appear on Home and in search
  (search uses the server's own Search, where it has one).
- **Links:** paste a link into search to open it, or save it in Settings › Links to keep it
  on Home. A radio station's `.pls` or `.m3u` plays as the station; a list of songs opens as an
  album; HLS (`.m3u8`) plays as one stream.

More to follow — the goal is every free source there is. [`AUDIO.md`](AUDIO.md) lists
every current and planned source, with a dated change log.

## What's next

- **More sources:** dozens more are listed in [`AUDIO.md`](AUDIO.md), from federated
  catalogs (Funkwhale, Mirlo, Faircamp) to historical archives (UCSB cylinders, Gallica)
  and more public-domain collections.
- **Explore:** browsing by decade and by source.
- **Android TV:** a layout made for the remote (the app installs and opens on TV now,
  but screens are still the phone's).
- **Library:** saved albums and concerts, plus export and import of the whole library.
- **Downloads** for offline listening, wherever the source allows it.
- **Settings:** audio quality for Wi-Fi and mobile data,
  a sleep timer, and an in-app check for new Kairoify releases.
- **Search:** artist, album and playlist results, a "Top results" strip, and source
  filters.
- **Licenses:** an open-source notices screen.
- **Sound:** volume normalization (each track brought to the same loudness, not just
  compressed), using ReplayGain tags where files have them.
- **Releases:** signed release builds published automatically by GitHub Actions.
- **Protocols still waiting:** Canimus (artist-owned catalogs) and ActivityPub
  (federated music) wait for a published spec and services that host freely licensed audio.
  Everything else on the protocol list is on `main` (see above).
- **Further out:** Kairo, the companion AI, as a DJ.

Not planned: video of any kind (music videos, concerts on PeerTube or Owncast, movies, TV),
Chromecast (it needs Google Play Services), and a Kairoify server.

## License

Kairoify is licensed under the **Common Public Attribution License Version 1.0
(CPAL-1.0)** — see [`LICENSE`](LICENSE). Works built on Kairoify must display the
attribution described in [`NOTICE.md`](NOTICE.md).

The Kairoify name and logo are not licensed. Forks must use their own name and
logo (see [`NOTICE.md`](NOTICE.md)).

Music played through Kairoify belongs to its artists and is subject to the terms of
the service it comes from. Creative Commons and public-domain recordings are shown with
their license in the player and on their album page, wherever the source publishes it.
