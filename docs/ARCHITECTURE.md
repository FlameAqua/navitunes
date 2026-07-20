# Navitunes — Architecture & Systems Reference

> Context doc for development. Navitunes is a **native Android** (Kotlin + Jetpack
> Compose, Material3) client for a self-hosted **Navidrome / Subsonic** music server,
> plus a companion **homelab "upload server"** that adds features Navidrome/Subsonic
> don't have (Spotify-sourced downloads via spotdl, uploads, and beets metadata fixing).
>
> Package root: `ie.adrianszydlo.navitunes` · applicationId `ie.adrianszydlo.navitunes`
> (debug: `.debug`, versionName suffix `-dev`). Current release: **v0.7.0** (versionCode 7).
>
> **Server upkeep, troubleshooting and recovery commands live in
> [MAINTENANCE.md](MAINTENANCE.md)** — read that before touching beets/spotdl/playlists.

---

## 1. High-level shape

```
┌─────────────────────────────┐         Subsonic REST (JSON)        ┌────────────────────┐
│  Navitunes (Android app)    │ ──────────────────────────────────▶│  Navidrome  :4533  │
│  Kotlin + Compose + Media3  │   /rest/*.view  (library, stream,   │  (music library,   │
│                             │    star, search, playlists, genres) │   scrobble, auth)  │
│                             │                                     └────────────────────┘
│                             │         Custom HTTP (JSON)          ┌────────────────────┐
│                             │ ──────────────────────────────────▶│ upload-server :3001│
│                             │   /health /upload /remove           │ (Node/Express)     │
│                             │   /download /cancel /tracks /fix    │   ├─ spotdl        │
│                             │                                     │   ├─ beets         │
│                             │         Spotify Web API             │   └─ writes /music │
│                             │ ─────────────▶ (discovery search)   └────────────────────┘
└─────────────────────────────┘   api.spotify.com (client-creds)      Caddy :8080 fronts
                                                                       both, public domain
                                                                       music.adrianszydlo.ie
```

Two servers, one box. Navidrome owns the library and playback API. The upload server is
a thin custom layer for the things a stock Subsonic server can't do. Both sit behind Caddy
and share the **same Subsonic credentials** (the upload server verifies callers by pinging
Navidrome with the token the app passes).

---

## 2. Android app

### 2.1 Build / module

- Single Gradle module `:app`. Version catalog: `gradle/libs.versions.toml`.
- Key libs: Compose BOM `2026.06.01`, Kotlin `2.4.0`, AGP `9.2.1`, Media3 `1.10.1`,
  OkHttp `5.4.0`, Retrofit `3.0.0` (models via kotlinx.serialization `1.11.0`),
  Coil `2.7.0`, Room `2.8.4`, WorkManager `2.11.2`, DataStore `1.2.1`,
  Palette `1.0.0`, Glance `1.1.1`.
- `compileSdk 37`, `minSdk 26`, `targetSdk 35` (targetSdk held at 35 deliberately — it's a
  runtime-behavior opt-in, not a build requirement).
- Version is overridable from the release tag: `-PversionName=… -PversionCode=…`.

### 2.2 Dependency injection

Hand-wired, **no DI framework**. `AppContainer` ([AppContainer.kt](../app/src/main/java/ie/adrianszydlo/navitunes/AppContainer.kt))
owns every singleton (all `by lazy`), lives for the process lifetime, and is created by
`NavitunesApp`. Access anywhere via `NavitunesApp.container()`. `onProfileSwitched()`
invalidates per-profile caches on account change.

### 2.3 Package map

```
ie.adrianszydlo.navitunes
├─ NavitunesApp.kt          Application; owns AppContainer
├─ AppContainer.kt          hand-wired singletons
├─ MainActivity.kt          hosts Compose, resolves ThemeMode, sets status/nav bar
├─ data/
│  ├─ LibrarySignals.kt     process-wide "library changed" nudge (StateFlow tick)
│  ├─ api/                  ApiClient (Subsonic over OkHttp), SubsonicModels (@Serializable)
│  ├─ auth/                 Profile, ProfileStore, SubsonicAuth (salted-MD5 token), Md5
│  ├─ repo/                 LibraryRepository (reads), PlaybackRepository (star/scrobble)
│  ├─ prefs/               AppPreferences (DataStore), RecentlyPlayedStore
│  ├─ offline/             Room-backed offline downloads (DownloadDb/Repository/Worker,
│  │                        OfflineResolver, StoragePermission) — streams library songs
│  │                        to public Music/ folder for offline play ("Device Downloads")
│  ├─ remote/              server-side stack (see §2.6): DownloadService, DownloadManager,
│  │                        MetadataFixService + MetadataFixManager ("Server Downloads")
│  ├─ lyrics/              LrcLibService — free online lyrics fallback (lrclib.net)
│  ├─ upload/              UploadService (push local files up to the server)
│  ├─ discovery/           SpotifyClient (Spotify Web API discovery search)
│  └─ update/             UpdateService + ApkInstaller (GitHub-release self-update)
├─ playback/
│  ├─ PlayerService.kt      Media3 MediaSessionService + ExoPlayer (the real player)
│  └─ PlayerController.kt   Compose-facing MediaController wrapper; StateFlows.
│                           Also playStream() + isLiveStream for internet radio.
└─ ui/
   ├─ theme/               NavColors token system, Theme, Type (Geist), Color, ArtColor
   ├─ common/              Cards, Motion, Skeleton, Notifier, SectionCard, TopBar,
   │                        ArtImage, States, AlphabetScrollbar, SongActionsFactory,
   │                        SheetDragHandle (finger-following sheet dismiss), TextPromptDialog
   ├─ nav/                 RootNav (bottom nav + NavHost + PlayerDock + dialogs + locals),
   │                        ManagePlaylistSheet (rename/description/public/delete)
   ├─ radio/               RadioScreen — internet stations + song radio (§2.8)
   ├─ home/ library/ search/ detail/ settings/ downloads/ profile/ login/ update/
   ├─ player/             MiniPlayer, FullPlayerSheet, PlayerDock (bubble), LyricsPanel
   └─ widget/             Glance home-screen widget
```

### 2.3.1 What V0.6 added

- **Radio tab** (`ui/radio/`) — bottom nav is now Home · Library · **Search (centred, accent-filled)** ·
  Radio · Settings. See §2.9.
- **Playlist management** — long-press a playlist (Library row or Home card) → rename, edit
  description, public/private, delete. Navidrome has **no API for a custom playlist cover**, so
  that isn't offered.
- **In-screen search** on album / artist / playlist detail (client-side filter of the loaded list).
- **Genre categories** (`ui/library/GenreCategories.kt`) — keyword-maps fine-grained tags into broad
  browsable categories, A–Z sidebar for the raw list, and treats spotdl's generic `Music` tag as
  *Uncategorized*. Category cards deliberately show **genre count, not a track total** — a song with
  several genres is counted once per genre server-side, so a summed total would overstate.
- **Lyrics** — falls back to **lrclib.net** (free, keyless, synced LRC preferred) when the server
  has none; the expanded view has faded top/bottom edges.
- **Server install** — album/artist detail has a cloud button that resolves the entity on Spotify
  *first*, then prompts with whether it matched a **single** or a full **album** (and warns if you
  already own the track) before queuing it on the server.
- **Reorderable queue**, drag-to-reorder with an explicit remove button; tapping a track plays it
  without closing the queue.
- **Live fix progress** — Settings → Maintenance shows the server job's stage/step, survives leaving
  the screen (server owns the state) and disables the button while running (§2.10).

### 2.3.2 What V0.7 added

- **Media notification / output switcher** — prev · ±10s · play/pause · next · **favourite**, with the
  heart synced **both ways** between the app and the notification (the service is the single writer;
  state broadcasts back over `setSessionExtras` → `MediaController.Listener.onExtrasChanged`). Prev/Next
  sit in the **primary** back/forward slots so they survive the compact strip. Live radio emits no
  custom buttons (no seek/favourite). Constants: `ACTION_TOGGLE_FAVORITE`, `EXTRA_IS_FAVORITE`.
- **Endless Song Radio** — `PlayerController.playSongRadio(seed, initial)` remembers the seed and
  auto-appends more similar songs when playback comes within 2 tracks of the end. Also reachable from
  a track's long-press menu and the player's *Go to…* menu. Shared builder `ui/radio/SongRadio.kt`.
- **Sleep timer** is absolute (a single wall-clock deadline, unaffected by track changes) and **fades
  the gain out** over ~4s before pausing, then restores it.
- **App-level volume** (`AppPreferences.volume`, applied via `MediaController.volume`) lets the user
  go below the Bluetooth absolute-volume floor. Lives in the player's Playback-settings panel.
- **Now-playing indicator everywhere** — `LocalNowPlaying` (id + playing) is collected once in
  `RootNav` and read by `SongRow` by default, so album/playlist/library/search all mark the current
  track with no per-screen wiring.
- **Player controls reorg** — leftmost **Go to…** menu (album/artist/Song Radio); rightmost
  **Playback settings** panel (speed, sleep timer, **skip silence** — moved out of app Settings —
  and volume). Album art toggles the lyrics view; tapping a synced lyric line seeks to it.
- **Radio glyph** placeholder (not a letter) for streams in the mini/full/bubble players.
- **Full string extraction underway** — ~200 UI strings moved to `res/values/strings.xml` (incl.
  `<plurals>`); groundwork for localisation (e.g. a future Polish `values-pl`). Some screens still
  hold inline literals.
- **Search** opens focused with the keyboard raised.

### 2.4 Theming (the one non-obvious pattern)

Colours are **theme-reactive design tokens**, not constants:

- `NavColors` is a data class of semantic slots (`accent`, `bg`, `text2`, `surface`, …).
  `DarkNavColors` / `LightNavColors` / Sequoia (warm) variants exist.
- Provided through `LocalNavColors` (**`compositionLocalOf`**, not `static…` — so a theme
  change recomposes only readers, which is what makes the animated theme **crossfade** work).
- Call sites still read plain names like `Accent`, `Text3`, `Surface`. Those are top-level
  **`@Composable @ReadOnlyComposable` getter `val`s** in `theme/Color.kt` that return
  `LocalNavColors.current.<slot>`. This is the trick that let ~21 screens migrate to full
  theming with near-zero churn — don't "simplify" them back to constants.
- `ThemeMode` (System/Light/Dark/Sequoia) is persisted in DataStore; `MainActivity`
  resolves System via `isSystemInDarkTheme()` and drives the window bars.
- Fonts: **Geist + Geist Mono** (bundled variable fonts in `res/font/`, built via
  `FontVariation`; file needs `@OptIn(ExperimentalTextApi::class)`). Fraunces was removed.
- Full player derives an ambient gradient/accent from cover art via AndroidX **Palette**
  (`ui/theme/ArtColor.kt`); rest of the app uses static terracotta `#D97757`.

### 2.5 Playback

- `PlayerService` = Media3 `MediaSessionService` holding the ExoPlayer (background playback,
  lockscreen/notification, media buttons).
- `PlayerController` is the Compose bridge: a `MediaController` wrapper exposing StateFlows
  (`currentItem`, `isPlaying`, `position`, `speed`, queue, `sleepEndMs`, …) and commands
  (`setSpeed`, `startSleepTimer`, `playNext`, `addToQueue`, `removeFromQueueAt`, `stop`).
- On track change, `updateFromMediaItem` re-reads the **authoritative** starred state from
  the server (`libraryRepository.song(id)?.starred`) so the favorite heart is correct when
  re-opening a previously-favorited song.
- UI: `MiniPlayer` ⇄ `PlayerDock`'s draggable **bubble** (long-press mini → bubble; snaps to
  edges; drag to trash to dismiss; tap to open full player). `FullPlayerSheet` has icon-only
  speed/sleep controls, a thin custom seek bar (no end dot), and an always-present lyrics
  card (`LyricsPanel` / `rememberSongLyrics`).

### 2.6 The server-download stack (`data/remote/`)

This is the most safety-critical client logic because the server is **single-flight** and
downloads are slow. Three pieces:

- **`DownloadService`** — raw HTTP to the upload server. `isAlive()` (`/health`, <500 = up),
  `isDownloading()` (reads `downloading` flag from `/health`), `requestDownload(message)`
  → `Outcome.{Success|Busy(409)|Failure}`, `requestCancel()` (`/cancel`), plus a now-mostly
  unused `resolveTracks()` (`/tracks`). JSON uses `encodeDefaults = true; ignoreUnknownKeys`.
- **`DownloadManager`** — process-scoped resilient queue (survives navigation; state lives
  here, not in composables). Guarantees: **one at a time** (mirrors server single-flight);
  **liveness-gated** (probes `/health` before each request; unreachable ⇒ wait+retry, not
  fail); **retry with backoff** (`MAX_ATTEMPTS=4`, 5s→15s→45s cap 60s); **dedup** (re-search
  of an in-flight entity shows "downloading", never a duplicate request). Since the server
  returns **202 immediately** and downloads in the background, `Success` only means
  *accepted* — the manager then calls `awaitServerIdle()` (polls `isDownloading()` up to
  `MAX_JOB_MS=30min`) before marking DONE, so the row doesn't say "Added to your library"
  mid-download. `cancel(key)` / `cancelServerJob()` kill the spotdl process server-side.
- **`MetadataFixService`** — POST `/fix`, triggered by the Genres tab "Re-fetch" button.

### 2.7 Two different "download" concepts (don't confuse them)

- **Offline downloads** (`data/offline/`, Room + WorkManager): copy *existing library songs*
  to the phone's public `Music/` folder for offline playback. Purely client-side.
- **Server downloads** (`data/remote/`, above): ask the *server* to fetch *new* music from
  Spotify (via spotdl) into the library. Cross-network, queued server-side, slow.

The UI names them explicitly — **"Device Downloads"** vs **"Server Downloads"** — because
conflating them was a real source of confusion.

### 2.8 Sheets & gestures

Bottom sheets use `containerColor = Color.Transparent` with the **content carrying its own
background**, plus `sheetGesturesEnabled = false` and a `SheetDragHandle`. Two reasons: dragging
the body used to dismiss the sheet by accident while scrolling, and an opaque sheet container
meant only the content slid while a dark rectangle stayed behind. The handle drives a
finger-following offset that settles back up or animates out past a threshold.

The mini-player can be turned into the floating bubble by **long-press or swipe-up**.

### 2.9 Radio (`ui/radio/`)

Two things in one tab:
- **Stations** — Navidrome's internet radio stations (`getInternetRadioStations`), with
  add/edit/delete (`create|update|deleteInternetRadioStation`). Subsonic exposes **no artwork**
  for stations, so they render a default icon tile.
- **Song radio** — seed from a recent/favourite track → `getSimilarSongs2`, falling back to
  `getTopSongs` for the artist, then `getRandomSongs`.

Stations play through `PlayerController.playStream()` (a raw URL, not a library song). While
`isLiveStream` is true the full player hides seek/±10s/speed/shuffle/repeat/sleep/lyrics/queue and
shows a LIVE badge — and **pause→play re-opens the stream** so you resume at the live edge rather
than replaying buffered audio.

### 2.10 Metadata-fix progress

`MetadataFixManager` (process-scoped, in `AppContainer`) polls `/fix/status` while a job runs and
exposes a `StateFlow<FixStatus>`. Because the **server owns the state**, progress survives leaving
Settings, backgrounding, and app restarts — the screen just re-reads it on entry. The button is
disabled while `running`, and the library signal is ticked when the job completes. Stage labels
come from the script's `[n/N] stage…` echoes, so adding a pipeline step needs no app change.

---

## 3. Endpoint communication

### 3.1 Navidrome / Subsonic  (`data/api/ApiClient.kt`)

- URL shape: `{server}/rest/{endpoint}.view?u=&t=&s=&v=1.16.1&c=Navitunes&f=json`.
- Auth: **salted token** — `t = md5(password + salt)`, a fresh random `salt` **per call**
  (`SubsonicAuth`). Password is never sent in the clear.
- Used for everything library/playback: `getAlbumList2`, `getArtist(s)`, `getAlbum`,
  `getPlaylists`, `getPlaylist`, `getStarred2`, `search3`, `getGenres`, `getSongsByGenre`,
  `getLyrics`/`getLyricsBySongId`, `star`/`unstar`, `scrobble`, `stream`, `getCoverArt`,
  playlist create/update/delete. Responses map to `@Serializable` types in `SubsonicModels.kt`.
- `ApiClient` also exposes `coverUrl`, `streamUrl`, `downloadUrl`, and the shared `okHttp`
  used by the Spotify/upload/download/update services.

### 3.2 Upload server  (custom, same box)

All routes take the Subsonic auth params as query string; the server validates them against
Navidrome before doing work.

| Method | Route          | Purpose |
|--------|----------------|---------|
| GET    | `/health`      | Liveness + `{ status, downloading, active, queued }`. `downloading` = a job is active **or queued**. |
| POST   | `/upload`      | Multipart file → `/music/Uploads` → triggers a Navidrome scan. |
| POST   | `/remove`      | Delete a file (fuzzy match; ambiguous matches rejected). |
| POST   | `/download`    | Body `{ message: "track\|album\|artist\|playlist <spotifyId>" }`. **Returns 202 instantly** and appends to a **FIFO queue** (§4). Concurrent callers are all accepted and drained one at a time; de-duplicated by entity. |
| POST   | `/cancel`      | SIGKILL the job currently downloading; the next queued job starts automatically. |
| POST   | `/tracks`      | `spotdl save` → track list JSON. Slow/flaky on big playlists; currently **unused** by the app. |
| POST   | `/fix`         | Starts `/opt/beets/fix-metadata.sh` via `spawn`, streaming progress. 202 + status, or 409 + live status if already running. |
| GET    | `/fix/status`  | `{ running, step, steps, stage, ok, message, elapsedMs }` — polled by the app for live progress. |

### 3.3 Spotify Web API  (`data/discovery/SpotifyClient.kt`)

- **Client-Credentials** flow (app-level token; no user login) for **discovery search only**.
- Hard limitation: editorial/algorithmic playlists return **403**. That's why Spotify-sourced
  *downloads* go through the server's spotdl (anonymous client), not this API.

---

## 4. Server-side implementation (homelab)

Everything below runs on a single Proxmox LXC; the app only talks HTTP to it.

- **Reverse proxy** — Caddy (`/etc/caddy/Caddyfile`), public `music.adrianszydlo.ie`,
  listens on `:8080`, routes the custom paths to the upload server and everything else to
  Navidrome. Note a front proxy timeout (~90s) — this is *why* `/download` had to become
  async 202 instead of blocking until spotdl finished.
- **Navidrome** — `:4533`. Owns the library DB, streaming, scrobble, auth. Scans `/music`.
- **Upload server** — Node/Express `/opt/upload-server/server.js`, `127.0.0.1:3001`
  (typically under pm2). Implements §3.2. Downloads go through a **FIFO queue**: requests are
  always accepted (202) and drained **one at a time**, because parallel spotdl runs into the same
  folder race and can OOM the box. A watchdog SIGKILLs a job stuck past 15 min. `/fix` is spawned
  (not `execFile`) so its `[n/N] stage…` stdout can be parsed into live progress.
- **spotdl** — does the actual Spotify→audio fetching (anonymous client, so it reads editorial
  playlists the Web API blocks). Invoked with an **output template** so downloads land in real
  album folders:
  `--output '{album-artist}/{album}/{track-number} - {title}.{output-ext}'`
  This matters: beets matches a *folder* to a release, so a flat dump of unrelated singles gets
  force-matched to one bogus album (this is how a Chappell Roan track ended up filed under
  *Various Artists / "Lay All Your Love On Me"* with track number 01). Playlists additionally get
  `--m3u`. **Gotchas:** OOM-killed / hangs on large playlists; slow enough to blow proxy timeouts.
- **beets** (`/opt/beets`, via pipx) + **`fix-metadata.sh [--deep]`** — the metadata pipeline
  invoked by `/fix` and by cron (nightly quick, weekly `--deep`):
  `import` → *(deep: `mbsync` → `lastgenre`)* → `write` → `duplicates` (log only) →
  **normalise artists** → Navidrome `startScan?fullScan=true` → **sync playlists (API)**.
  Config in `/opt/beets/config.yaml`; `import.move: yes`, `quiet_fallback: asis`.

- **`normalize-artists.sh` (+ `split-artists.py`)** — fixes spotdl's artist tagging: single-artist
  "Various Artists" → real artist; multi-artist `A/B` → multi-valued `ARTISTS`/`TPE1` tags with the
  primary artist as album-artist. See MAINTENANCE.md §5.9.

- **`sync-playlists-api.sh`** — the piece that keeps playlists alive, and it runs **last, after the
  rescan**. Navidrome **0.54+ stores paths relative to the library root** and its built-in m3u
  importer won't match reliably, so instead of rewriting m3u paths we read each `.m3u8`, resolve every
  entry to a Navidrome song ID from `navidrome.db` (library-relative path, then title), and rebuild
  the playlist through the Subsonic API (`createPlaylist?playlistId=…&songId=…`). Immune to Navidrome's
  path-matching and to file moves. **Replaced the retired `fix-playlist-paths.sh` repointer.**
  See MAINTENANCE.md §5.1.
  **Hard-won lessons (see genre saga):**
  - beets only acts on files that are **in `library.db`**. If imports have been skipping
    (quiet mode drops uncertain matches; the old `-i` incremental flag skipped whole dirs),
    the DB is nearly empty and `lastgenre`/`write` do nothing. Check with `beet stats`.
  - `beet lastgenre` defaults to **album mode** + **no-force**; for a singleton-heavy
    spotdl library you need `beet lastgenre -A -f` (items mode, overwrite the generic
    `genre=Music` tag spotdl writes), else it processes zero items.
  - To catalog existing files without retagging/moving: `beet import -A` (noautotag).
  - Genre coverage is only as good as Last.fm — expect broad genres, many songs untagged.
    (V0.6: compound genres into major categories for a smart-recommendation algorithm.)

---

## 5. Debugging on a physical phone (adb)

The maintainer keeps a physical Android phone for on-device testing — use it to reproduce,
inspect logs, screenshot, and verify UI that the emulator can't (real gestures, background
playback, lockscreen, the bubble player).

- **adb**: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`
  (Bash: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`).
- **Confirm it's attached:** `adb devices -l` (it may be disconnected — if the list is empty,
  ask the user to plug in / accept the USB-debugging prompt before assuming a code problem).
- **Debug app id:** `ie.adrianszydlo.navitunes.debug` (installs *alongside* the release build;
  already signed into the `music.adrianszydlo.ie` profile).
- **Build + install:**
  ```bash
  ./gradlew.bat :app:assembleDebug
  adb install -r app/build/outputs/apk/debug/app-debug.apk       # add -s <serial> if >1 device
  adb shell monkey -p ie.adrianszydlo.navitunes.debug 1          # launch
  ```
- **Logs (most useful filter):**
  ```bash
  adb logcat -s Navitunes/Download Navitunes/DownloadMgr Navitunes/Http
  ```
- **Screenshot:** `adb exec-out screencap -p > shot.png` (Git Bash: prefix with
  `MSYS_NO_PATHCONV=1` if using an on-device `/sdcard/...` path).
- **Toggle system dark mode** (test theme follow): `adb shell cmd uimode night yes|no`.

---

## 6. Guardrails / conventions

- Commit only when explicitly asked; work happens on version branches (e.g. `V0.5`), never
  push without being told. Commit-message trailer: `Co-Authored-By: Claude …`.
- **No secrets in the repo.** Server credentials (Navidrome pass, Spotify client secret,
  AcoustID key) live only on the server — never echo, commit, or hard-code them.
- App changes are presentation/feature work; playback/auth/data *logic* is stable — preserve
  existing dedup/liveness/retry behaviour in the download stack rather than rewriting it.
- The `_dev` debug build is safe to install repeatedly; it won't clobber the release install.
