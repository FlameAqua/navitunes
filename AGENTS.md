# Navitunes — project context for Codex

Native **Android** (Kotlin + Jetpack Compose, Material3) client for a self-hosted
**Navidrome / Subsonic** server, plus a companion homelab **upload server** (spotdl
downloads, uploads, beets metadata). Package root `ie.adrianszydlo.navitunes`.
Current release **v0.6.0**.

**Full architecture, endpoints, and server-side details:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Read it before non-trivial work — it covers the theming token trick, the server-download
queue, the two servers, and the beets pipeline gotchas.

**Server upkeep / troubleshooting:** [docs/MAINTENANCE.md](docs/MAINTENANCE.md) — the runbook for
beets, spotdl, playlists, duplicates and the fix pipeline. Read it before advising on anything
server-side; several classes of bug there look like app bugs but aren't.

## Fast facts
- **DI:** hand-wired `AppContainer` (no framework); access via `NavitunesApp.container()`.
- **Two servers, one box:** Navidrome (`:4533`, Subsonic REST — library/stream/search/star)
  and the custom upload server (`/download /upload /remove /cancel /fix /health`), both
  behind Caddy at `music.adrianszydlo.ie`, sharing Subsonic auth.
- **Subsonic auth:** salted token `t = md5(password+salt)`, fresh salt per call. No secrets in repo.
- **Theming:** colours are theme-reactive tokens. `Accent`/`Text3`/`Surface` etc. are
  `@Composable @ReadOnlyComposable` getter vals reading `LocalNavColors.current` — do NOT
  turn them back into constants (breaks light/dark/Sequoia + the theme crossfade).
- **Downloads = two unrelated things:** `data/offline/` = **Device Downloads** (copy library songs
  to phone) vs `data/remote/` = **Server Downloads** (server fetches new music from Spotify via
  spotdl — 202 + poll-to-done, retry/backoff; don't rewrite the resilience logic). The server now
  **queues** concurrent requests instead of 409-rejecting them.
- **Playlists are rebuilt via the Subsonic API, not Navidrome's m3u import.** Navidrome 0.54+ stores
  paths relative to the library root and its m3u importer won't match the files, so `sync-playlists-api.sh`
  (last pipeline step, after the rescan) reads each `/music/Uploads/*.m3u8`, resolves entries to song
  IDs from `navidrome.db`, and rebuilds the playlist over the API. The old `fix-playlist-paths.sh`
  repointer is **retired**. If a playlist "loses" songs, re-run the sync — see MAINTENANCE.md §5.1,
  don't go hunting in app code.
- **Artist tagging is normalised server-side** by `normalize-artists.sh` (+ `split-artists.py`):
  single-artist "Various Artists" → real artist; multi-artist `A/B` → multi-valued `ARTISTS` tags,
  primary as album-artist. It does **not** fix bogus *album* names (spotdl sometimes stamps unrelated
  singles with one compilation album, or leaks a remix descriptor into the artist list) — MAINTENANCE.md §5.9.
- **Never auto-delete on a fuzzy duplicate match.** Many singles share `albumartist = Various
  Artists`, so grouping by albumartist+title collides *distinct* songs. Group by `$artist`.
- **Navidrome quirks that look like app bugs:** album `songCount` can exceed the tracks it returns
  (phantom rows — needs `fullScan=true`); `search3` omits artist `coverArt` (fall back to the
  artist id); genre counts double-count multi-genre songs.

## Build / debug
```bash
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:compileDebugKotlin      # fast compile check
./gradlew.bat testDebugUnitTest
```
Physical **phone debugging** is available (see docs §5): adb at
`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`, debug app id
`ie.adrianszydlo.navitunes.debug` (installs alongside release). `adb devices -l` first —
it's often unplugged, so an empty list means "ask the user to connect", not a bug.
Useful logcat tags: `Navitunes/Download`, `Navitunes/DownloadMgr`, `Navitunes/Http`.

## Conventions
- Commit only when asked; work on version branches (e.g. `V0.5`), don't push unless told.
- Presentation/feature work is fine to change freely; auth/playback/data *logic* is stable.
