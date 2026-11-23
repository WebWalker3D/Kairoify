# Kairoify

**The encyclopedia of free music. Open-source radio.**

Kairoify puts every free source of music under one app: live radio from every
country, recorded live concerts, public-domain recordings, Creative Commons
catalogs, and the free tiers of mainstream services. Search them all at once,
browse the world by country, genre and era, and play it all through one queue
and one library.

- No accounts, no ads, no tracking.
- Every source can be switched on or off (coming with Settings).
- Everything runs on your device — no Kairoify server in between.

## Status

**Phase 2: songs and favorites.**

- **Live radio:** browse and search ~50,000 stations from every country, and see
  what song is playing.
- **Songs:** one search across YouTube Music, SoundCloud and Bandcamp. Play any
  result, or start a radio from a YouTube Music song.
- **Favorites:** tap ♡ on anything playing, or long-press any station or song. The
  Library tab keeps one list with Play all and Shuffle. A queue of stations skips
  like the preset buttons on a car radio.
- **In the background:** playback continues with notification controls. Android
  Auto shows your Favorites too.

More sources follow in the next phases.

Platform: Android 8.0 and newer, 64-bit phones (nearly every phone since 2017).

## Build it yourself

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 21, the Android SDK and Python 3.14 on the `PATH` (for the embedded
Python's packages). Every push also builds a debug APK in GitHub Actions.

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
| Internet Archive | Live Music Archive concerts, Great 78 Project, netlabels, public-domain classical | Planned |
| Jamendo | Creative Commons music | Planned |
| Audius | Independent artists | Planned |

More to follow — the goal is every free source there is.

## License

Kairoify is licensed under the **Common Public Attribution License Version 1.0
(CPAL-1.0)** — see [`LICENSE`](LICENSE). Works built on Kairoify must display the
attribution described in [`NOTICE.md`](NOTICE.md).

The Kairoify name and logo are not licensed. Forks must use their own name and
logo (see [`NOTICE.md`](NOTICE.md)).

Music played through Kairoify belongs to its artists and is subject to the terms of
the service it comes from. When Creative Commons sources arrive, their tracks will be
shown with their artist and license.
