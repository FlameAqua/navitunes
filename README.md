# Navitunes

A native Android client for [Navidrome](https://www.navidrome.org/) (and any Subsonic-compatible server), built as a proper APK — background playback, lockscreen controls, BT headset keys, multi-account support, and offline downloads.

Based on the [`navitunes_old`](./..) PWA blueprint, rewritten in Kotlin + Jetpack Compose with Media3 ExoPlayer.

---

## Features

- **Multi-profile** — Add several Navidrome accounts (different users, or different servers entirely). Switch from Settings.
- **Library** — Albums, artists, playlists, favorites. Same browse model as the PWA.
- **Full-screen player** — Shuffle, repeat (off / all / one), favorite toggle, ±10s skip, queue view.
- **Background playback** — Media3 `MediaSessionService` keeps audio playing when the app is backgrounded, with system media notification and lockscreen artwork.
- **Bluetooth + headset keys** — Play/pause/next via earbuds and car stereos.
- **Offline downloads** — Per-profile download manager, Wi-Fi-only option, storage usage in Settings.
- **Encrypted credentials** — Passwords stored in `EncryptedSharedPreferences` with a Keystore-backed master key. Passwords are never sent in cleartext: every request uses the Subsonic salted-MD5 token (`md5(password + salt)`).
- **No third-party services** — No analytics, no crash reporters, no ads. The only outbound traffic is to *your* Navidrome.

---

## Install

### Option A: prebuilt APK from GitHub Releases (recommended)

1. Open this repo's [Releases page](https://github.com/adrianszydlo/navitunes/releases).
2. Download the latest `app-release.apk`.
3. On your Android device, enable **Settings → Apps → Special access → Install unknown apps** for your browser or file manager.
4. Open the downloaded APK and tap **Install**.
5. Launch Navitunes. Enter your Navidrome URL (e.g. `https://music.adrianszydlo.ie`), username, and password. Tap **Connect**.

> Minimum: Android 8.0 (API 26). Target: Android 15 (API 37).

### Option B: build from source

You need **JDK 17** and the **Android SDK**. Either:

- **Android Studio** (recommended) — install from [developer.android.com/studio](https://developer.android.com/studio). Open the project; Studio prompts you to install the matching SDK (compileSdk 37) on first sync and writes `local.properties` for you. Run **Build → Build APK(s)**.
- **Command-line tools only** — install JDK 17, then download the [Android command-line tools](https://developer.android.com/studio#command-tools), unzip to `C:\Android\Sdk\cmdline-tools\latest\`, then:

  ```powershell
  $env:ANDROID_HOME = "C:\Android\Sdk"
  & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" `
      "platform-tools" "platforms;android-34" "build-tools;34.0.0"
  ```

Then build:

```powershell
# Copy local.properties.example -> local.properties and set sdk.dir to your SDK
Copy-Item local.properties.example local.properties
# First clone only — initialise the Gradle wrapper jar:
gradle wrapper --gradle-version 8.9
.\gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Drag-and-drop it onto a running emulator, or `adb install` to a connected device.

For a release build that you can install on a real phone, see **Signing** below.

---

## Profiles

Profiles map 1:1 to Navidrome user accounts. You can have multiple profiles on the same server (each user sees their own favourites, playlists, and play counts) or profiles across different servers.

- **Add** — Settings → Profiles → *Add profile*. Enter the server URL, username, and password. Navitunes verifies the credentials with a `ping.view` call before saving.
- **Switch** — Settings → tap a profile. Playback stops, in-memory caches clear, and the library reloads against the new profile. Offline downloads stay scoped per-profile.
- **Remove** — Settings → swipe / tap the logout icon. Removing a profile also deletes any offline content downloaded under it.

Credentials are stored in `EncryptedSharedPreferences` (AES-256-GCM, key in Android Keystore). The store file is excluded from cloud backup and device-to-device transfer.

---

## Signing release builds

To produce signed release APKs (required to install over USB):

1. Generate a keystore once:

   ```bash
   keytool -genkey -v -keystore release.keystore -alias navitunes \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `release-keystore.properties` next to `settings.gradle.kts`:

   ```properties
   storeFile=/absolute/path/to/release.keystore
   storePassword=••••••
   keyAlias=navitunes
   keyPassword=••••••
   ```

   This file is in `.gitignore` — never commit it.

3. Build:

   ```bash
   ./gradlew :app:assembleRelease
   ```

### CI-driven signing

The GitHub Actions workflow (`.github/workflows/android.yml`) signs tagged release builds when these repository secrets are present:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.keystore` output |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias (e.g. `navitunes`) |
| `KEY_PASSWORD` | key password |

Tag a commit `v1.0.0` (or similar) and CI will produce a signed release APK, upload it as an artifact, and attach it to a GitHub Release.

---

## Project layout

```
app/src/main/java/ie/adrianszydlo/navitunes/
├── NavitunesApp.kt           Application — owns the AppContainer
├── AppContainer.kt           Hand-wired DI container (no Hilt/Koin)
├── MainActivity.kt           Single-activity Compose host
├── data/
│   ├── auth/                 MD5, Subsonic auth params, profile store
│   ├── api/                  Subsonic models + ApiClient (OkHttp + kotlinx-serialization)
│   ├── repo/                 LibraryRepository + PlaybackRepository
│   ├── offline/              Room DB + WorkManager-backed DownloadWorker
│   └── prefs/                DataStore-backed user preferences
├── playback/
│   ├── PlayerService.kt      MediaSessionService — owns the single ExoPlayer
│   └── PlayerController.kt   Compose-friendly MediaController wrapper
└── ui/                        Compose screens (Home, Library, Search, Detail, Player, Settings…)
```

---

## Security & privacy

- The default network security config permits HTTP because Navidrome is commonly self-hosted on a LAN. The login screen warns when a non-HTTPS URL is entered. **Use HTTPS over the public internet.**
- Backup rules exclude credentials and offline files from cloud backup.
- App permissions are kept to the minimum: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` (mediaPlayback), `POST_NOTIFICATIONS`, `WAKE_LOCK`. No external storage, no camera, no contacts, no location.
- Release builds are minified and shrunk via R8 with custom rules for kotlinx-serialization, Retrofit, Media3, and Room.

---

## Uploading songs to your library

Navidrome — and the Subsonic API in general — has **no upload endpoint**: it scans a filesystem directory for music. To make the in-app "Upload song" button work, you need to stand up a small receiver on your Apache wrapper that takes the multipart POST, drops the file into Navidrome's music directory, and lets Navidrome rescan.

Navitunes sends the file as `multipart/form-data` with field name `file`, plus the standard Subsonic auth params (`u`, `t`, `s`, `v`, `c`, `f`) on the query string — so the receiver can verify the caller without ever seeing a plaintext password.

The upload server should also expose a sibling `DELETE /remove?songId=…` endpoint so the in-app "Remove song from library" menu works. Both routes share auth verification.

### Recommended: Node + Express receiver

```js
// upload-server.js
const express = require('express');
const multer  = require('multer');
const path    = require('path');
const fs      = require('fs');

const app       = express();
const MUSIC_DIR = '/music/Uploads';
const ND_URL    = 'http://127.0.0.1:4533';
const ALLOWED_USERS = ['your_navidrome_user'];

async function verifyToken(u, t, s) {
  if (!ALLOWED_USERS.includes(u)) return false;
  try {
    const resp = await fetch(
      `${ND_URL}/rest/ping.view?u=${u}&t=${t}&s=${s}&v=1.16.1&c=upload&f=json`
    );
    const data = await resp.json();
    return data['subsonic-response']?.status === 'ok';
  } catch { return false; }
}

const requireAuth = async (req, res, next) => {
  const { u, t, s } = req.query;
  if (!await verifyToken(u, t, s)) return res.status(401).json({ error: 'Unauthorized' });
  next();
};

// --- Upload ---
const storage = multer.diskStorage({
  destination: (req, file, cb) => { fs.mkdirSync(MUSIC_DIR, { recursive: true }); cb(null, MUSIC_DIR); },
  filename:    (req, file, cb) => cb(null, file.originalname)
});
const upload = multer({
  storage,
  limits: { fileSize: 200 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    const ok = ['.mp3','.flac','.m4a','.aac','.ogg','.opus','.wav']
      .includes(path.extname(file.originalname).toLowerCase());
    cb(ok ? null : new Error('Unsupported format'), ok);
  }
});

app.post('/upload', requireAuth, upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No file' });
  const { u, t, s } = req.query;
  fetch(`${ND_URL}/rest/startScan.view?u=${u}&t=${t}&s=${s}&v=1.16.1&c=navitunes&f=json`).catch(() => {});
  res.json({ status: 'ok', file: req.file.originalname });
});

// --- Remove (only files under MUSIC_DIR get touched) ---
app.delete('/remove', requireAuth, async (req, res) => {
  const { u, t, s, songId } = req.query;
  if (!songId) return res.status(400).json({ error: 'songId required' });

  // Resolve song path from Navidrome.
  const r = await fetch(`${ND_URL}/rest/getSong.view?u=${u}&t=${t}&s=${s}&v=1.16.1&c=navitunes&f=json&id=${songId}`);
  const j = await r.json();
  const songPath = j['subsonic-response']?.song?.path;
  if (!songPath) return res.status(404).json({ error: 'Song not found' });

  // Path Navidrome returns is relative to its music root. Confirm it lives
  // under our Uploads directory before touching the disk.
  const absolute = path.resolve('/music', songPath);
  const safeRoot = path.resolve(MUSIC_DIR) + path.sep;
  if (!absolute.startsWith(safeRoot)) {
    return res.status(403).json({ error: 'Only uploaded files can be removed' });
  }

  try { fs.unlinkSync(absolute); } catch (e) { return res.status(500).json({ error: e.message }); }
  fetch(`${ND_URL}/rest/startScan.view?u=${u}&t=${t}&s=${s}&v=1.16.1&c=navitunes&f=json`).catch(() => {});
  res.json({ status: 'ok' });
});

app.use((err, req, res, next) => res.status(400).json({ error: err.message }));
app.listen(3001, '127.0.0.1', () => console.log('Upload server on :3001'));
```

And in your Caddyfile, expose **both** routes:

```caddy
:8080 {
    handle /upload* { reverse_proxy 127.0.0.1:3001 }
    handle /remove* { reverse_proxy 127.0.0.1:3001 }
    handle          { reverse_proxy 127.0.0.1:4533 }
}
```

In Navitunes Settings → Upload, set the URL to `https://music.example.com:8080` (just the base — the app appends `/upload` and `/remove` itself). If you have an existing URL with `/upload` at the end, it still works; the suffix is stripped.

### Alternative: minimal PHP receiver

Drop this as `/var/www/music.adrianszydlo.ie/upload.php` and protect it behind HTTPS (no /remove route in this minimal variant):

```php
<?php
// upload.php — Navitunes upload receiver.
$NAVIDROME_MUSIC = '/var/lib/navidrome/music';
$ALLOWED_USERS = [
    'adrian'   => 'YOUR_NAVIDROME_PASSWORD',
    // add other users you trust to upload
];

// Verify the Subsonic salted token.
$u = $_GET['u'] ?? '';
$t = $_GET['t'] ?? '';
$s = $_GET['s'] ?? '';
if (!isset($ALLOWED_USERS[$u]) || md5($ALLOWED_USERS[$u] . $s) !== $t) {
    http_response_code(401); exit('Unauthorized');
}

if (!isset($_FILES['file'])) { http_response_code(400); exit('Missing file'); }
$f = $_FILES['file'];

$name = basename($f['name']);
if (!preg_match('/\.(mp3|flac|m4a|aac|ogg|opus|wav)$/i', $name)) {
    http_response_code(415); exit('Unsupported format');
}
$dest = $NAVIDROME_MUSIC . '/Uploads/' . $name;
@mkdir(dirname($dest), 0775, true);
if (!move_uploaded_file($f['tmp_name'], $dest)) {
    http_response_code(500); exit('Could not save');
}
chmod($dest, 0644);
echo 'ok';
```

Then in Navitunes: **Settings → Upload → Set URL** to `https://music.adrianszydlo.ie/upload.php`. Pick an audio file → it gets POSTed to your server, dropped in the `Uploads/` subfolder, and Navitunes also calls `startScan.view` so Navidrome picks up the new track in the next scan cycle.

**Format whitelist (client side):** `mp3 · flac · m4a · aac · ogg · opus · wav`
**Size cap:** 200 MB per file.

## Roadmap

The PWA had a clean, focused feature set — Navitunes Android matches it 1:1 and adds the things only a real app can deliver (background playback, system controls, offline). Future work, in roughly that order:

- Long-press song menus (Download, Play next, Add to queue, View album)
- Smart playlists (filter by genre/year/rating)
- Cast / Chromecast support
- Wear OS companion
- Android Auto / Automotive

---

## License

MIT (see [LICENSE](LICENSE)).
