# Navitunes — Security & Codebase Audit

_Audit performed ahead of making the repository public. Scope: the Android app
(`app/`), build/signing configuration, CI, and dependency supply chain._

## Summary

The codebase is in good shape for a public, sideloaded app. Credentials are
stored correctly (Keystore-backed encryption), backups are disabled, secrets are
kept out of version control, and network logging is debug-only. No high-severity
issues were found. A few lower-severity items and hardening opportunities are
listed below, with the accepted-risk rationale where applicable.

## Findings

| # | Severity | Area | Finding | Status |
|---|----------|------|---------|--------|
| 1 | ✅ Good | Credentials at rest | Profiles stored in `EncryptedSharedPreferences` (AES-256-GCM, Keystore master key). | Pass |
| 2 | ✅ Good | Backups | `allowBackup=false` + custom data-extraction/backup rules prevent credential exfiltration via `adb backup`. | Pass |
| 3 | ✅ Good | Network logging | OkHttp logging interceptor is gated to `BuildConfig.DEBUG` at `BASIC` level — no headers/bodies, and disabled entirely in release. | Pass |
| 4 | ✅ Good | Secrets in VCS | Keystore, `release-keystore.properties`, `*.apk`, `local.properties` are git-ignored; CI injects signing material from secrets. | Pass |
| 5 | 🟡 Low | Discovery secret at rest | The user-supplied Spotify **client secret** is stored in plaintext DataStore, not encrypted storage. | Accepted / see rec. |
| 6 | 🟡 Low | Transport | Global `cleartextTrafficPermitted="true"` to support LAN Navidrome. | Accepted (documented) |
| 7 | 🟡 Low | Broad permission | `MANAGE_EXTERNAL_STORAGE` (All-Files-Access) for the public downloads folder. | Accepted (sideload) |
| 8 | 🟢 Info | Repo hygiene | `local.properties.example` shipped a real machine SDK path (leaked a username). | **Fixed** in this pass |
| 9 | 🟢 Info | Updater | `REQUEST_INSTALL_PACKAGES` added for in-app updates. | Mitigated (below) |

## Rationale & recommendations

**#5 — Spotify client secret.** Blast radius is limited: it's the user's *own*
Spotify developer app secret, used only for the Client-Credentials metadata search,
and is trivially rotatable at developer.spotify.com. Recommendation (non-blocking):
move it alongside the Navidrome credentials in `EncryptedSharedPreferences`. The
Navidrome password — the sensitive credential — is already encrypted.

**#6 — Cleartext traffic.** Self-hosted Navidrome frequently runs on a private
network without TLS. The app permits cleartext but warns at login on non-HTTPS
URLs. Users reaching their server over the public internet should use HTTPS.

**#7 — All-Files-Access.** Chosen so downloads land in a public `Music/Navitunes`
folder that survives app reinstalls. This is acceptable for a sideloaded app but
would be restricted on Google Play; if Play distribution is ever pursued, switch
to app-scoped storage or the MediaStore API.

**#9 — Update mechanism.** APKs are fetched over HTTPS from this repo's Releases.
Android's installer enforces **same-signing-key** before applying an in-place
update, so a substituted APK is rejected by the OS. Installs are user-confirmed in
the system UI; the app never installs silently. Release integrity is further
supported by a published `sha256` checksum per release.

## Supply chain / SBOM

- Every CI run and every release generates a **CycloneDX SBOM** (`anchore/sbom-action`).
- The SBOM is scanned with **Grype** (`anchore/scan-action`, fail-on-`high`).
- **Dependabot** watches Gradle deps and GitHub Actions weekly.
- **`dependency-review-action`** blocks PRs that introduce high-severity vulnerable deps.
- **CodeQL** (`java-kotlin`) runs on push, PR, and weekly.

To generate an SBOM locally:

```bash
# using Syft (https://github.com/anchore/syft)
syft dir:. -o cyclonedx-json=navitunes-sbom.cyclonedx.json
grype sbom:navitunes-sbom.cyclonedx.json
```

## Verifying a release download

```bash
sha256sum -c navitunes-vX.Y.Z.apk.sha256
```
