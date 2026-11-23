# Sources of audio

Every source Kairoify plays from today, every one being built, and every one planned.
Kairoify only uses free, legal sources. Each source plays through that service's own
public API or website, under that service's terms.

Every change to a source (added, changed, switched on or off, removed) gets a dated
line in the [change log](#change-log) at the bottom, and its row moves to the right
table.

**Two separate questions for every source**
- **Status** (is it built?):
  - **Live:** in the app.
  - **Built (off):** finished and tested, but not switched on.
  - **Planned:** not built yet.
- **Authorization** (does the provider allow it?):
  - **Official:** played through the provider's own public API or public streams, as they intend.
  - **Official, key:** the official API, but it needs a registered key (built off until there is one).
  - **Not authorized:** played by extracting the audio (yt-dlp), which the provider's terms don't allow. Kept only where the owner chose to include the source.
  - **Blocked:** the provider technically blocks plays from apps (for example hotlink protection).
  - **To confirm:** public endpoints, but the developer terms still need checking.

## Live

| Source | What it brings | How Kairoify reaches it | Authorization | Since |
|---|---|---|---|---|
| [Radio Browser](https://www.radio-browser.info) | ~50,000 live radio stations from every country, with the song that's playing | Public JSON API; each station's own public stream | Official | 0.1 |
| [Internet Archive](https://archive.org) — Live Music Archive | ~300,000 live concerts recorded with the bands' permission, plus "On this day" | Archive search and metadata APIs | Official | 0.3 |
| Internet Archive — Great 78 Project | ~190,000 digitized 78 rpm records, 1900s–1950s | Archive APIs | Official | 0.3 |
| Internet Archive — Netlabels | ~77,000 free netlabel releases | Archive APIs | Official | 0.3 |
| Internet Archive — Classical | Public-domain classical recordings | Archive APIs | Official | 0.3 |
| [Audius](https://audius.co) | Independent artists on an open music network, plus trending | Public REST API, including its stream endpoint | Official | 0.3 |
| [Wikimedia Commons](https://commons.wikimedia.org) | Classical, folk, historical and international recordings, each with its license | MediaWiki API | Official | 0.4 |
| [NTS Radio](https://www.nts.live) — live | NTS 1 and NTS 2, live from London | NTS's public API and public stream relay | Official | 0.4 |
| [hearthis.at](https://hearthis.at) | DJ sets, live recordings and tracks | Public read API and its stream URLs | To confirm (developer terms) | 0.4 |
| [YouTube Music](https://music.youtube.com) | The mainstream catalog, plus "Start radio" from any song | ytmusicapi for search, yt-dlp for streams, on the phone | **Not authorized** (owner's choice to include) | 0.2 |
| [SoundCloud](https://soundcloud.com) | Indie tracks, DJ sets, remixes | yt-dlp search and streams | **Not authorized** (owner's choice to include) | 0.2 |
| NTS Radio — archive | ~90,000 shows, played from their SoundCloud or Mixcloud recordings | NTS's public API, then yt-dlp | **Not authorized**, same as SoundCloud and Mixcloud | 0.4 |
| [Mixcloud](https://www.mixcloud.com) | DJ mixes and radio shows | Mixcloud's public search, yt-dlp for HLS streams | **Not authorized**: its API excludes audio streams (royalty reporting and licensing) and its terms restrict extraction. Included by the owner's choice. | 0.4 |
| [Bandcamp](https://bandcamp.com) | Independent artists | Bandcamp's own search endpoint (unofficial), yt-dlp for streams | **Not authorized** (owner's choice to include) | 0.2 |

## Built (off)

| Source | What it brings | Authorization | What it would take |
|---|---|---|---|
| [Jamendo](https://www.jamendo.com) | ~600,000 Creative Commons songs, shown with their license | Official, key | Register a client ID at developer.jamendo.com, then build with `-Pkairoify.jamendo.clientId=…` (see the README) |
| [ccMixter](https://ccmixter.org) | Creative Commons remixes and originals, with their license | Blocked (hotlink protection) | Its MP3s answer 403 unless the request carries ccMixter's own page as the Referer. Owner decision: send that header, or ask ccMixter. |

## Planned

| Source | What it brings | How Kairoify will reach it | Key? |
|---|---|---|---|
| [Openverse](https://openverse.org) | Creative Commons audio gathered from many sites | Public REST API (rate-limited); each upstream's rights need checking | No |
| [Library of Congress](https://www.loc.gov/audio/) | Historical American music: jazz, blues, opera | Public JSON API, filtered to recordings with clear rights | No |
| [National Jukebox](https://www.loc.gov/collections/national-jukebox/) | 5,882 recordings from 1900–1922, public domain in the US | Library of Congress open dataset (JSON metadata and MP3s) | No |
| [Citizen DJ](https://citizen-dj.labs.loc.gov) | Public-domain historical music and recordings | Downloadable datasets; some overlap with the Library of Congress | No |
| [Funkwhale](https://www.funkwhale.audio) | Federated music libraries and artist releases | Funkwhale REST API on public instances | No (public instances) |
| [Open Audio](https://open.audio) | Creative Commons music, including a Free Music Archive mirror | Funkwhale API; each track's license needs checking | No |
| [Dogmazic](https://play.dogmazic.net) | Tens of thousands of freely licensed independent tracks (since 2004) | Ampache/Subsonic API, which supports third-party apps | To check (may need an account) |
| [Free Music Archive](https://freemusicarchive.org) | Creative Commons independent music | Through Open Audio's mirror or Internet Archive copies; FMA has no public API | No |
| [Europeana](https://www.europeana.eu) | European historical and cultural recordings | Search API, filtered by rights and media availability | **Needs key** (built off) |
| [Freesound](https://freesound.org) | Experimental, ambient and musical recordings | Official REST API, free for noncommercial use | **Needs key** (built off) |
| [Mirlo](https://mirlo.space) | Independent artists and albums | Canimus API, authorized HLS streaming | Check terms |
| [Faircamp](https://simonrepp.com/faircamp/) | Artist-hosted music catalogs | Catalog discovery and federation | No |
| Fairplayer | Federated independent music | Canimus-compatible catalogs | No |
| [Wavlake](https://wavlake.com) | Independent music with optional artist tipping | Public RSS feeds | No |
| Bandwagon | Federated independent artists | ActivityPub | Needs permission (playback) |
| [The Indie Beat Radio](https://theindiebeat.fm) | 5,000+ opted-in independent tracks | Radio streams | Needs permission (third-party terms) |
| The Indie Beat Live | Live independent concerts | HLS stream published for external players | No |
| Tunestr | Independent live performances | Nostr-based discovery | Needs permission |
| [Newgrounds Audio Portal](https://www.newgrounds.com/audio) | Electronic, gaming and independent music | License-filtered catalog | Needs permission (automated access) |
| [Ampwall](https://ampwall.com) | Independent bands and artists | Official playback integration | Needs permission |
| [jam.coop](https://jam.coop) | Cooperative independent music | Official playback integration | Needs permission |
| [Subvert](https://subvert.fm) | Artist-owned music marketplace | Partnership | Needs permission |
| [SoundClick](https://www.soundclick.com) | Independent artists, hip-hop and beats | Free listening exists | Needs permission (third-party rights) |
| [ReverbNation](https://www.reverbnation.com) | Emerging bands and musicians | — | Needs permission |
| ResonTune | Independent, freely streamable releases | Emerging platform | To check (availability and API) |
| [Tribe of Noise](https://www.tribeofnoise.com) | Independent international artists | Catalog and API | To check (terms) |
| [Musopen](https://musopen.org) | Classical recordings | — | To check (recording rights, automated access; API key) |
| [IMSLP](https://imslp.org) | Classical performances and compositions | — | To check (copyright differs by country) |
| [Open Music Archive](https://www.openmusicarchive.org) | Historical jazz, blues and early recordings | — | To check (UK public-domain status) |
| [UCSB Cylinder Audio Archive](https://www.library.ucsb.edu/special-research-collections/cylinder-archive) | 15,000+ early cylinder recordings | — | To check (rights per item) |
| [Raita](https://raita.fi) | Early Finnish recordings | CC0 catalog; OAI-PMH and OpenSearch | No |
| [Virtual Gramophone](https://www.bac-lac.gc.ca) | ~7,300 digitized Canadian recordings | — | To check (rights by country) |
| [Gallica](https://gallica.bnf.fr) | Historical French and European music | — | Noncommercial reuse with attribution |
| Yale Berliner Gramophone Collection | Early gramophone recordings | Rights-filtered digital collections | To check |
| [Smithsonian Open Access](https://www.si.edu/openaccess) | American cultural and historical recordings | Open Access API (CC0 audio where available) | To check (API key) |
| Ohio State Public Domain Day Project | Newly recorded public-domain compositions | CC0 recordings | No |
| [Public Domain Review](https://publicdomainreview.org) | Curated historical audio | Plays from the original archives it links to | No |
| Netlabels by name, through the Internet Archive | Ektoplazm (psytrance), Kahvi Collective (ambient/IDM), blocSonic (hip-hop/electronic), 8Bitpeoples (chiptune), Dusted Wax Kingdom (downtempo), Phonocake, Petroglyph Music, Mahorka, insectorama, weareallghosts, ODrex Music, S27, Comfort Stand, Monotonik, Thinner (minimal techno), Textone, Webbed Hand Records, Test Tube, Peppermill Records, Zeromoon | The existing Internet Archive source, as one browse entry per label wherever the Archive mirrors its catalog; labels it doesn't mirror get checked one by one | No |
| [KEXP](https://www.kexp.org) | Seattle's listener-supported station | The live stream is already in Radio Browser; the archive has no public audio | — |
| Podcasts | Shows, via open RSS feeds and podcast directories | RSS | No |
| Public-domain audiobooks ([LibriVox](https://librivox.org)) | Volunteer-read public-domain books | LibriVox API and Internet Archive | No |
| Your own music files | Music already on your phone, alongside everything streamed | Android's media library | — |

## Planned protocols

These aren't single services. Each one can unlock many sources at once.

| Protocol | What it unlocks |
|---|---|
| Icecast directory | More internet radio stations |
| RSS audio enclosures | Music podcasts, artist releases, DJ shows |
| Podcasting 2.0 | Independently distributed music, with optional artist tipping |
| Canimus | Artist-owned music catalogs |
| ActivityPub | Federated music distribution |
| OpenSubsonic | Compatible music servers |
| Ampache API | Community and personal music collections |
| PeerTube API | Music videos and concerts (audio-only in Kairoify unless the owner decides otherwise) |
| Owncast | Independent live performances |
| M3U / M3U8 | Authorized radio and media playlists |
| PLS / XSPF | More playlist formats |
| Direct HTTP audio | Artist-published MP3, FLAC, AAC and Ogg streams |
| WebDAV | Music collections you authorize on a remote server |
| UPnP / DLNA | Music you authorize on your local network |

## Ideas the design rules out or leaves to the owner

These came up on the wishlist but conflict with decisions already in the design, so
they wait for an owner decision:
- **Music and concert video** (including PeerTube): the app plays audio only.
- **Movies and TV:** outside a music app.
- **Chromecast:** needs Google Play Services, which the app avoids.
- **Spotify/Apple Music playback:** not free sources. Playlist *import*, matching
  songs to free sources, is planned instead.

## Change log

Newest first. Dates are when the change landed on `main`. "Unreleased" means it isn't
in a published release yet.

| Date | Release | Source | Change |
|---|---|---|---|
| 2026-11-23 | 0.4.0 | Wikimedia Commons | **Fixed**: search failed on the phone because some metadata values aren't text; checked on a Samsung S22+ along with NTS, Mixcloud and hearthis.at. |
| 2026-11-23 | 0.4.0 | Mixcloud | **Switched back on** by the owner's choice, marked Not authorized like YouTube Music, SoundCloud and Bandcamp. NTS shows recorded on Mixcloud are back. |
| 2026-11-23 | 0.4.0 | NTS Radio, hearthis.at, Wikimedia Commons | **Switched on** in the app (next release). **Fixed**: NTS search now filters by your words; a hearthis.at search with no matches shows no results instead of an outage; Wikimedia MIDI files (which can't play) are left out. |
| 2026-11-23 | 0.4.0 | Mixcloud | **Switched off**: its API excludes audio streams and its terms restrict extraction. NTS shows recorded only on Mixcloud are left out. |
| 2026-11-23 | 0.4.0 | All sources | **Changed**: this file now shows authorization separately from build status. |
| 2026-11-23 | 0.4.0 | ccMixter | **Built, off**: its files refuse plays from outside ccmixter.org (403 without a ccMixter Referer); waiting on the owner. |
| 2026-11-23 | 0.4.0 | Wikimedia Commons | **Added** (in progress): search across Commons audio, artist and license shown; plays the file itself. |
| 2026-11-23 | 0.4.0 | 14 streaming protocols (Icecast, RSS, Podcasting 2.0, Canimus, ActivityPub, OpenSubsonic, Ampache, PeerTube, Owncast, M3U/M3U8, PLS/XSPF, direct HTTP, WebDAV, UPnP/DLNA) | **Planned**: added to the roadmap. |
| 2026-11-23 | 0.4.0 | ccMixter | **Added** (in progress): search, Creative Commons remixes and originals with their license; plays the MP3. |
| 2026-11-23 | 0.4.0 | 20 netlabels (Ektoplazm, Kahvi, blocSonic, Thinner, Monotonik and others) | **Planned**: through the Internet Archive where it mirrors them. |
| 2026-11-23 | 0.4.0 | hearthis.at | **Added** (in progress): search; DJ sets and tracks played from hearthis.at's own streams. |
| 2026-11-23 | 0.4.0 | NTS Radio | **Added** (in progress): live channels NTS 1 and NTS 2 on Home; show archive search, played from each show's SoundCloud or Mixcloud recording. |
| 2026-11-23 | 0.4.0 | Mixcloud | **Added** (in progress): search, DJ mixes played as HLS through yt-dlp. |
| 2026-11-23 | 0.4.0 | NTS Radio, hearthis.at, ccMixter, Wikimedia Commons | **Started** (in progress). |
| 2026-11-23 | 0.4.0 | Mirlo, Faircamp, Fairplayer, Wavlake, Bandwagon, The Indie Beat (Radio, Live), Tunestr, Newgrounds, Ampwall, jam.coop, Subvert, SoundClick, ReverbNation, ResonTune, Tribe of Noise, Musopen, IMSLP, Open Music Archive, UCSB Cylinder Archive, Raita, Virtual Gramophone, Gallica, Yale Berliner, Smithsonian, Ohio State PD Day, Public Domain Review | **Planned**: added from the owner's wishlists. |
| 2026-11-23 | 0.4.0 | Openverse, Library of Congress, National Jukebox, Citizen DJ, Funkwhale, Open Audio, Dogmazic, Europeana, Freesound | **Planned**: added to this list from the owner's wishlist. |
| 2026-11-23 | 0.3.0 | Internet Archive | **Added**: Live Music Archive, Great 78 Project, netlabels and classical; album pages; "On this day" on Home. |
| 2026-11-23 | 0.3.0 | Internet Archive | **Changed**: search matches every word (not one exact phrase); server errors show as failures you can retry. |
| 2026-11-23 | 0.3.0 | Audius | **Added**: search, full-length streams, "Trending on Audius". |
| 2026-11-23 | 0.3.0 | Jamendo | **Built, off**: needs a registered client ID. |
| 2026-11-23 | 0.3.0 | YouTube Music, SoundCloud, Bandcamp | **Changed**: one unplayable song no longer stops Play all; a broken service stops after three failures in a row. |
| 2026-11-23 | 0.2.0 | YouTube Music | **Added**: search, "Start radio", playback through yt-dlp. |
| 2026-11-23 | 0.2.0 | SoundCloud | **Added**: search and HLS playback through yt-dlp. |
| 2026-11-23 | 0.2.0 | Bandcamp | **Added**: search through Bandcamp's autocomplete, playback through yt-dlp. |
| 2026-11-23 | 0.2.0 | yt-dlp (engine) | **Added**: on-phone engine for YouTube Music, SoundCloud and Bandcamp; updates itself daily from yt-dlp's official releases. |
| 2026-11-23 | 0.1 (unreleased build) | Radio Browser | **Added**: ~50,000 live stations, search, browse by country and genre, the song playing. |

