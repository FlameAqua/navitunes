# Navitunes — project context for Claude

Native **Android** (Kotlin + Jetpack Compose, Material3) client for a self-hosted
**Navidrome / Subsonic** server, plus a companion homelab **upload server** (spotdl
downloads, uploads, beets metadata). Package root `ie.adrianszydlo.navitunes`.
Current release **v0.5.0**.

**Full architecture, endpoints, and server-side details:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Read it before non-trivial work — it covers the theming token trick, the server-download
queue, the two servers, and the beets pipeline gotchas.

## Fast facts
- **DI:** hand-wired `AppContainer` (no framework); access via `NavitunesApp.container()`.
- **Two servers, one box:** Navidrome (`:4533`, Subsonic REST — library/stream/search/star)
  and the custom upload server (`/download /upload /remove /cancel /fix /health`), both
  behind Caddy at `music.adrianszydlo.ie`, sharing Subsonic auth.
- **Subsonic auth:** salted token `t = md5(password+salt)`, fresh salt per call. No secrets in repo.
- **Theming:** colours are theme-reactive tokens. `Accent`/`Text3`/`Surface` etc. are
  `@Composable @ReadOnlyComposable` getter vals reading `LocalNavColors.current` — do NOT
  turn them back into constants (breaks light/dark/Sequoia + the theme crossfade).
- **Downloads = two unrelated things:** `data/offline/` (copy library songs to phone) vs
  `data/remote/` (server fetches new music from Spotify via spotdl — single-flight, 202 +
  poll-to-done, retry/backoff; don't rewrite the resilience logic).

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
