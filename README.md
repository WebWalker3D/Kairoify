# Kairoify

**The encyclopedia of free music. Open-source radio.**

Kairoify puts every free source of music under one app: live radio from every
country, recorded live concerts, public-domain recordings, Creative Commons
catalogs, and the free tiers of mainstream services. Search them all at once,
browse the world by country, genre and era, and play it all through one queue
and one library.

- No accounts, no ads, no tracking.
- Every source can be switched on or off.
- Everything runs on your device — no Kairoify server in between.

## Status

**Phase 1: worldwide live radio.** Browse and search ~50,000 stations from every
country, see what song is playing, and listen in the background with notification,
lock-screen and Bluetooth controls. More sources follow in the next phases.

Platform: Android 8.0 and newer.

## Build it yourself

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 21 and the Android SDK. Every push also builds a debug APK in GitHub Actions.

## Android Auto

Kairoify shows up in Android Auto with "Live now in <your country>" and "Most popular
worldwide". Because it isn't installed from the Play Store, Android Auto only lists
it after you enable **Unknown sources** in Android Auto's developer settings (open
Android Auto settings, tap the version number ten times to unlock developer
settings).

## Planned sources

| Source | What it brings |
|---|---|
| Radio Browser | ~50,000 live radio stations, every country |
| Internet Archive | Live Music Archive concerts, Great 78 Project, netlabels, public-domain classical |
| Jamendo | Creative Commons music |
| Audius | Independent artists |
| YouTube Music | The mainstream catalog |
| SoundCloud | Indie, DJ sets, remixes |
| Bandcamp | Independent artists |

More to follow — the goal is every free source there is.

## License

Kairoify is licensed under the **Common Public Attribution License Version 1.0
(CPAL-1.0)** — see [`LICENSE`](LICENSE). Works built on Kairoify must display the
attribution described in [`NOTICE.md`](NOTICE.md).

The Kairoify name and logo are not licensed. Forks must use their own name and
logo (see [`NOTICE.md`](NOTICE.md)).

Music played through Kairoify belongs to its artists and is subject to the terms of
the service it comes from. Creative Commons tracks are shown with their artist and
license.
