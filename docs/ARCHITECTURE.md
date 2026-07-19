# Navitunes — Architecture & Systems Reference

> Context doc for development. Navitunes is a **native Android** (Kotlin + Jetpack
> Compose, Material3) client for a self-hosted **Navidrome / Subsonic** music server,
> plus a companion **homelab "upload server"** that adds features Navidrome/Subsonic
> don't have (Spotify-sourced downloads via spotdl, uploads, and beets metadata fixing).
>
> Package root: `ie.adrianszydlo.navitunes` · applicationId `ie.adrianszydlo.navitunes`
> (debug: `.debug`, versionName suffix `-dev`). Current release: **v0.5.0** (versionCode 5).

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
│  │                        to public Music/ folder for offline play
│  ├─ remote/              server-side download stack (see §2.6):
│  │                        DownloadService, DownloadManager, MetadataFixService
│  ├─ upload/              UploadService (push local files up to the server)
│  ├─ discovery/           SpotifyClient (Spotify Web API discovery search)
│  └─ update/             UpdateService + ApkInstaller (GitHub-release self-update)
├─ playback/
│  ├─ PlayerService.kt      Media3 MediaSessionService + ExoPlayer (the real player)
│  └─ PlayerController.kt   Compose-facing MediaController wrapper; StateFlows
└─ ui/
   ├─ theme/               NavColors token system, Theme, Type (Geist), Color, ArtColor
   ├─ common/              Cards, Motion, Skeleton, Notifier, SectionCard, TopBar,
   │                        ArtImage, States, AlphabetScrollbar, SongActionsFactory
   ├─ nav/                 RootNav (bottom nav + NavHost + PlayerDock + dialogs + locals)
   ├─ home/ library/ search/ detail/ settings/ downloads/ profile/ login/ update/
   ├─ player/             MiniPlayer, FullPlayerSheet, PlayerDock (bubble), LyricsPanel
   └─ widget/             Glance home-screen widget
```

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
  Spotify (via spotdl) into the library. Cross-network, single-flight, slow.

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

| Method | Route        | Purpose |
|--------|--------------|---------|
| GET    | `/health`    | Liveness + `{ status, downloading }`. `downloading` = a spotdl job is active. |
| POST   | `/upload`    | Multipart file → `/music/Uploads` → triggers a Navidrome scan. |
| POST   | `/remove`    | Delete a file (fuzzy match; ambiguous matches rejected). |
| POST   | `/download`  | Body `{ message: "track\|album\|artist\|playlist <spotifyId>" }`. **Returns 202 instantly**, runs spotdl in the background (single-flight via `sendRunning` + a stale-lock watchdog; playlists get `--m3u`). 409 if already busy. |
| POST   | `/cancel`    | SIGKILL the current spotdl process. |
| POST   | `/tracks`    | `spotdl save` → track list JSON (bypasses the Spotify 403 on editorial playlists). Slow/flaky on big playlists; currently **unused** by the app after the per-track sheet was reverted. |
| POST   | `/fix`       | Runs `/opt/beets/fix-metadata.sh` (beets re-tag + genres + Navidrome rescan). |

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
  (typically under pm2). Implements §3.2. Single-flight download lock; when it sends the
  202 it must **not** touch `res` again in the spotdl callback (guard with
  `if (!res.headersSent)`), or it throws `ERR_HTTP_HEADERS_SENT`.
- **spotdl** — does the actual Spotify→audio fetching (uses an anonymous client, so it reads
  editorial playlists the Web API blocks). **Gotchas:** OOM-killed / hangs on large
  playlists; slow enough to blow proxy timeouts (hence async). Writes into `/music/Uploads`.
- **beets** (`/opt/beets`, via pipx) + **`fix-metadata.sh`** — metadata pipeline invoked by
  `/fix`: `beet import` → `mbsync` → `lastgenre` → `write` → Navidrome rescan. Config in
  `/opt/beets/config.yaml`; MusicBrainz + lastgenre (Last.fm) plugins.
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
