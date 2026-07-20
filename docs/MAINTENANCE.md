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
| Playlist repointer | `/opt/beets/fix-playlist-paths.sh` | Keeps `.m3u8` paths valid after beets moves files |
| Playlists | `/music/Uploads/*.m3u8` | m3u-backed; Navidrome auto-imports them |
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
6. **repoint playlists** — rewrite `.m3u8` paths to files' current locations
7. **rescan** — Navidrome `startScan?fullScan=true` (purges phantom records)

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
**Cause.** Playlists are m3u files with paths relative to `/music/Uploads`. When beets *moves* a track out of `Uploads`, that path dies; the next Navidrome scan re-imports the m3u and drops every entry it can't resolve.

**Fix.** The repointer (step 6) prevents this. To repair now:
```bash
export BEETSDIR=/opt/beets
for m in /music/Uploads/*.m3u8; do /opt/beets/fix-playlist-paths.sh "$m"; done
/opt/beets/fix-metadata.sh
```
Healthy output: `kept=N fixed=M missed=0`. Any `[MISS]` means that track couldn't be located — check it by hand:
```bash
beet ls -p title:"Some Title"
```
Originals are preserved as `<playlist>.m3u8.orig`.

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
