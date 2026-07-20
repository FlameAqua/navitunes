# Navitunes — Server Maintenance & Troubleshooting

> Practical runbook for the homelab box (Navidrome + upload-server + spotdl + beets).
> Everything here is run as root on the `navidrome` host unless stated otherwise.
> For app/code architecture see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## 1. The moving parts

| Thing | Where | Notes |
|---|---|---|
| Navidrome | `:4533` | Owns the library DB, streaming, playlists. v0.61.x |
| Upload server | `127.0.0.1:3001`, `/opt/upload-server/server.js` | pm2. `/health /upload /remove /download /cancel /fix /fix/status /tracks` |
| spotdl | `/usr/local/bin/spotdl` | Downloads into `/music/Uploads/{album-artist}/{album}/…` |
| beets | `/opt/beets` (`BEETSDIR`), `library.db` | Imports/organises/tags `/music/Uploads` → `/music/<Artist>/<Album>/` |
| Fix job | `/opt/beets/fix-metadata.sh` | The nightly pipeline (below) |
| Artist normaliser | `/opt/beets/normalize-artists.sh` (+ `split-artists.py`) | Fixes spotdl's "Various Artists" misfiling + splits `A/B` into multi-valued tags |
| Playlist sync | `/opt/beets/sync-playlists-api.sh` | Rebuilds Navidrome playlists from the `.m3u8` files **via the Subsonic API** (resolves each entry to a song ID). Replaced the old path-repointer. |
| Playlists | `/music/Uploads/*.m3u8` | Track lists the sync **reads**; Navidrome's own m3u import is **not** relied on (see §5.1) |
| Secrets | `/opt/beets/.nd-creds` (chmod 600) | `ND_USER` / `ND_PASS`. **Never commit.** |

**Cron**
```
0  4 * * *  /opt/beets/fix-metadata.sh          # nightly, fast (5 steps)
30 3 * * 0  /opt/beets/fix-metadata.sh --deep   # weekly backfill (7 steps)
```

---

## 2. The fix pipeline

`fix-metadata.sh [--deep]` runs, in order:

1. **import** — `beet import -q` on `/music/Uploads` (organises + tags + moves)
2. *(deep)* **mbsync** — refresh from MusicBrainz (only affects items with MB IDs)
3. *(deep)* **genres** — `beet lastgenre -A` (fills gaps only; no `-f`)
4. **write** — flush tags to files
5. **duplicates** — `beet duplicates` → **log only**, never auto-deletes
6. **normalise artists** — `normalize-artists.sh`: single-artist "Various Artists" → real artist;
   multi-artist `A/B` → multi-valued `ARTISTS`/`TPE1` tags + primary album-artist (see §5.9)
7. **rescan** — Navidrome `startScan?fullScan=true` (purges phantom records)
8. **sync playlists** — `sync-playlists-api.sh` rebuilds each playlist through the Subsonic API
   from resolved song IDs. **Runs last**, after the rescan, because it needs the scanned
   `media_file` rows to resolve IDs (see §5.1).

Trigger manually, or from the app (Settings → Maintenance → *Fix metadata now*, which shows live stage progress and disables the button while running).

```bash
/opt/beets/fix-metadata.sh            # quick
/opt/beets/fix-metadata.sh --deep     # full
tail -f /var/log/beets-fix.log        # watch
```

---

## 3. Routine health checks

```bash
export BEETSDIR=/opt/beets

beet stats                                   # track/album counts
ls /music/Uploads/*.mp3 2>/dev/null | wc -l   # should be ~0 after a fix run
ls /music | sort -f | uniq -di                # case-split artist folders (should be empty)
curl -s localhost:3001/health                 # {"status":"ok","downloading":false,...}
tail -40 /var/log/beets-fix.log               # last run
tail -20 /var/log/beets-skipped.log           # what beets couldn't match
```

**Near-duplicate check** (run occasionally, by hand — see §5.3 before deleting anything):
```bash
beet ls -f '$artist|$title' | tr 'A-Z' 'a-z' | sed 's/[^a-z0-9|]//g' | sort | uniq -d
```

---

## 4. Backups worth having

```bash
# playlists (the only thing that's genuinely hard to reconstruct)
mkdir -p ~/playlist-backup-$(date +%F)
find /music -iname '*.m3u*' -exec cp -v {} ~/playlist-backup-$(date +%F)/ \;

# beets DB
cp /opt/beets/library.db ~/beets-library-$(date +%F).db
```
The repointer also keeps a one-time `<playlist>.m3u8.orig` next to each playlist.

---

## 5. Troubleshooting

### 5.1 A playlist lost most of its songs
**Cause (historical + current).** Navidrome **0.54+ stores `media_file.path` relative to the library
root** (`Artist/Album/track.mp3`, no `/music/` prefix) and its built-in m3u importer will not
reliably match paths — the old repointer wrote absolute `/music/…` paths and Navidrome matched almost
none of them (confirmed: 1 of 137). So we **no longer rely on Navidrome's m3u import at all.**

**How it works now.** `sync-playlists-api.sh` (pipeline step 8, after the rescan) reads each `.m3u8`,
resolves every entry to a Navidrome song ID **straight from `navidrome.db`** (by library-relative
path, then by title), and rebuilds the playlist via the Subsonic API
(`createPlaylist?playlistId=…&songId=…`). This is immune to Navidrome's path-matching and to file
moves. The `.m3u8` files are just track lists it consumes.

**Fix / repair now:**
```bash
/opt/beets/sync-playlists-api.sh          # rebuild all playlists from their .m3u8 files
```
Healthy output: `updated: <name> (N tracks)`. If a playlist resolves to 0, check that the tracks are
actually in the library (`beet ls title:"Some Title"`) and that the scan has run.

> The old `fix-playlist-paths.sh` repointer and the `<playlist>.m3u8.orig` backups are **retired**.
> Do not reintroduce path-rewriting — it fights Navidrome's relative-path storage.

### 5.2 `/music/Uploads` isn't being organised
Check the skip log first:
```bash
tail -30 /var/log/beets-skipped.log
```
Common causes:
- **A flat dump of unrelated singles.** beets matches a *folder* to one release, so a flat folder becomes one bogus "album" and gets skipped (or force-matched to something absurd). Fixed by spotdl's `--output {album-artist}/{album}/{track-number} - {title}.{output-ext}` — verify it's still in `server.js`.
- **Quiet mode skipping.** `import: quiet_fallback: asis` in `/opt/beets/config.yaml` makes beets keep spotdl's (good) tags instead of skipping.

### 5.3 Duplicate songs / albums
```bash
beet duplicates -f '$path'      # exact matches
beet ls -f '$artist|$title' | tr 'A-Z' 'a-z' | sed 's/[^a-z0-9|]//g' | sort | uniq -d   # near matches
```
⚠️ **Never auto-delete on a fuzzy match.** Grouping by `$albumartist` produces false positives — many singles share `albumartist = Various Artists`, so distinct songs with the same title collide (e.g. two different tracks called "Ghost"). Always group by `$artist`, and inspect before removing:
```bash
beet ls -f '$id | $artist | $album | $bitrate | $path' title:"STYX HELIX"
beet remove -d id:<id>          # -d also deletes the file
/opt/beets/fix-metadata.sh      # repoint playlists + rescan
```
Note: the same track on a standard *and* deluxe edition is legitimate, not a duplicate.

### 5.4 Same artist appears twice (case split)
`asis` imports keep spotdl's capitalisation ("Pierce **The** Veil") while matched ones use MusicBrainz's ("Pierce **the** Veil"). Linux is case-sensitive → two folders → two artists.
```bash
ls /music | sort -f | uniq -di   # detect
beet modify -y albumartist:"Pierce The Veil" albumartist="Pierce the Veil" artist="Pierce the Veil"
beet move
find /music -type d -empty -delete
/opt/beets/fix-metadata.sh
```
(beets field queries are case-insensitive, so one command catches both variants.)

### 5.5 Album shows the wrong track count / ghost albums in the app
Navidrome keeps `media_file` rows for files that have moved or been deleted until a **full** scan reconciles them. Step 7 of the pipeline already passes `fullScan=true`. To force it:
```bash
source /opt/beets/.nd-creds
SALT=$(openssl rand -hex 6); TOKEN=$(printf '%s%s' "$ND_PASS" "$SALT" | md5sum | cut -d' ' -f1)
curl -s "http://127.0.0.1:4533/rest/startScan.view?u=$ND_USER&t=$TOKEN&s=$SALT&v=1.16.1&c=fixjob&f=json&fullScan=true"
```

### 5.6 Genres are sparse
Genres come from Last.fm via beets and are written **into the files** (Navidrome reads file tags, not `library.db`).
```bash
beet ls -f '$genres' | sort | uniq -c | sort -rn      # distribution
beet lastgenre -A -f && beet write                    # force a full refresh (slow)
```
- `lastgenre: auto: yes` means new imports get genres automatically.
- The nightly run omits `-f` so it only fills gaps; a full `-f` refresh re-queries every track at ~1 req/sec, so keep it manual/weekly.
- Coverage is only as good as Last.fm; expect broad genres and some noise.

### 5.7 Downloads are slow / stuck
```bash
curl -s localhost:3001/health          # {"downloading":true,"queued":N}
pm2 logs upload-server --lines 50
curl -s -X POST "localhost:3001/cancel?u=...&t=...&s=..."   # kill the active spotdl
```
`/download` is a **FIFO queue**: concurrent requests from multiple users are all accepted (202) and drained one at a time (parallel spotdl into one folder would race). A stuck job is killed by the watchdog after 15 min.

### 5.8 The fix job won't start
```bash
curl -s "localhost:3001/fix/status?u=...&t=...&s=..."   # {"running":true,"stage":"genres","step":3,...}
ls -l /tmp/beets-fix.lock                                # flock guard
pm2 restart upload-server
```
The app disables the button while `running` is true, so a 409 means a job really is in flight.

### 5.9 Songs show under "Various Artists", or a collaboration shows as one artist
**Cause.** spotdl tags many tracks with `albumartist = Various Artists` + `compilation = 1` (even
single-artist singles), and joins multiple artists into one string with `/`
(`Samuel Prince/Adrian Forsén`). Navidrome then files them under Various Artists and treats the joined
string as a single artist.

**Fix (automated in pipeline step 6, `normalize-artists.sh`):**
- Single-artist VA tracks → `beet modify albumartist=<artist> comp=0`.
- Multi-artist `A/B` → `split-artists.py` (mutagen) writes multi-valued `TPE1` + `TXXX:ARTISTS`
  and sets `TPE2` (album-artist) to the **primary** (first) artist, so the album is owned by a real
  artist, not a phantom "A, B, C" combined entity. Clears `TCMP`. Idempotent.

Run manually: `/opt/beets/normalize-artists.sh && <rescan>`.

**Bogus compilation dumps** (spotdl downloads a whole Spotify compilation — e.g. 200 tracks titled
"Crank That (Soulja Boy)" — stamps `album=` that name on all of them, and **beets groups the lot into
one album and forces a single album-artist onto every track**; `beet write` then reasserts that
album-artist on every run, clobbering any per-file fix). `normalize-artists.sh` **step 3** detects an
album with **≥4 distinct `artist` values** (not album-artist — beets forces that to one, so it's
useless as a signal) and rebuilds each track from its intact `artist`: `albumartist=artist`,
`album=title`, `comp=0`. This un-groups the album in beets so `write` stops reasserting. Real albums
have 1–3 distinct artists → untouched. Idempotent. **Caveat:** a genuine 4+-artist compilation you
*want* kept would also be split — whitelist it.

> The normaliser does **not** try to reconstruct *correct* album names, only to stop bogus grouping.
> A version/remix descriptor leaking into the artist list is separate source pollution — inspect raw
> tags (`ID3(path).getall('TALB'/'TXXX:ARTISTS')`) and fix by hand.

---

## 6. Performance notes

- **mbsync** only helps items that have MusicBrainz IDs. If most of your library was imported `asis`, it's a no-op — keep it in the `--deep` run only.
- **lastgenre** re-queries tracks that have *no* genre on every run (there's no negative cache), so it scales linearly. Weekly is the right cadence.
- **chroma** (acoustid fingerprinting) is expensive per file. Drop it from `plugins:` if it isn't earning its keep.

---

## 7. Security

- Credentials live in `/opt/beets/.nd-creds` (chmod 600), sourced by the fix script — **never inline in a script, never committed.**
- Subsonic auth is `md5(password + salt)` with a fresh salt per request; there is **no reusable API token**, so the password must be available to any job that calls the API. Alternatively enable Navidrome's own scan schedule/watcher and drop the `startScan` call entirely.
- Rotate immediately if a password is ever pasted into a terminal transcript or log.
