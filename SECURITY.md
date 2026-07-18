# Security Policy

## Supported versions

Navitunes is distributed as a sideloaded APK via GitHub Releases. Only the
**latest release** receives security fixes. The in-app updater and the
"Check for updates" button in Settings will prompt you when a newer release is
available.

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

- Use GitHub's **private vulnerability reporting**: the *Security* tab →
  *Report a vulnerability* (GitHub Security Advisories).
- Include the affected version, reproduction steps, and impact.

You'll get an acknowledgement within a few days. Once a fix is released, we're
happy to credit you in the release notes unless you'd prefer to stay anonymous.

## Security model & notes

- **Credentials at rest.** Navidrome profile credentials are stored in
  `EncryptedSharedPreferences`, encrypted with a hardware-backed key from the
  Android Keystore (AES-256-GCM). App backups are disabled
  (`android:allowBackup="false"`) so credentials can't be extracted via `adb backup`.
- **Transport.** HTTPS is strongly recommended. Cleartext HTTP is *permitted*
  because self-hosted Navidrome often runs on a private LAN
  (`http://192.168.x.x:4533`); the login screen warns when a non-HTTPS URL is
  entered. Prefer HTTPS + a valid certificate whenever the server is reachable
  off-LAN. Note that the Subsonic API's own auth (salted-token) is only as
  private as the transport — another reason to use TLS.
- **Update integrity.** Update APKs are downloaded over HTTPS from this repo's
  GitHub Releases. Android's package installer independently verifies that an
  update is signed with the **same key** as the installed app before applying it,
  so a tampered or third-party APK is rejected by the OS. The user confirms every
  install in the system UI; nothing installs silently.
- **Permissions of note.** `MANAGE_EXTERNAL_STORAGE` is used only to keep offline
  downloads in a public `Music/Navitunes` folder that survives reinstalls;
  `REQUEST_INSTALL_PACKAGES` is used only by the updater.
- **Supply chain.** Dependencies are pinned via the Gradle version catalog,
  monitored by Dependabot, and each build produces a CycloneDX SBOM that CI scans
  for known vulnerabilities (Grype, fail-on-high). CodeQL runs on every push/PR.

See [`docs/SECURITY-AUDIT.md`](docs/SECURITY-AUDIT.md) for the full audit.
