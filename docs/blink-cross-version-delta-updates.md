# BLINK cross-version delta updater (Testlab)

## Goal

An APK installed **ten or fewer compatible releases behind** can download a
single **base-to-latest patch**, not ten intermediate patches. Historical APKs
must still be publicly retrievable and compatible; there is no guarantee that
a particular version has a beneficial patch. **Full signed APK fallback is
always available**, including for older APKs, Play Store installs, missing
patches and devices with insufficient storage.

## Publisher

- Only the official verified publisher generates deltas after the new APK is
  signed. It retrieves up to ten previous released compatible signed APKs.
- Each target release publishes a `deltas.txt` manifest with a separate
  SHA-256 + size + base APK version/hash for each offered patch.
- Each delta contains COPY/INSERT byte instructions compressed with zlib;
  publisher *reconstructs and hashes* the resulting signed target APK before
  uploading, downloads every new asset back, and verifies all checksums again.
- Patches 85% or more of the new APK's size are omitted.
- An empty patch manifest means the old full APK flow runs unchanged.
- Releases built before this code remains compatible with clients because
  missing patch manifests automatically fall back to full APK downloads.

## Android website APK update

1. The official signed release identity is pinned by version and SHA-256.
2. BLINK checks `deltas.txt` for the exact installed version.
3. BLINK hashes the APK at Android's installed `sourceDir` and compares the
   manifest's base hash before even downloading its matching patch.
4. The patch download uses the same private WorkManager foreground job,
   pause/resume by validated HTTP byte ranges, strict bounds, official HTTPS
   redirect origin, and content checksum.
5. Shared pure-JVM code reconstructs a byte-identical, signed APK. Both the
   reconstructed bytes and Android package/signing identity are checked.
6. The normal user-approved Android system PackageInstaller performs the
   installation. **No app data migration, reinstall, clearing storage, or
   silent installation** is performed.
7. If the patch is absent, unsafe or corrupted, it is never installed.
   A clean full-APK download is used instead; transient networking retries
   retain partial patch bytes.

## Limitations and required QA

- Existing previous APKs are signed ZIPs, which may not delta-compress well
  (particularly obfuscated or compressed assets); actual patch sizes vary.
- Some Android bundle/split-install layouts do not match a full historical
  APK byte-for-byte; these intentionally use the full APK fallback.
- Keeping up to ten base versions costs release storage and publisher runtime.
- The **Android 15 emulator** gate tests signed debug-APK upgrade and a
  pre-existing private app-data marker, as a reproducible proxy for installer
  data preservation. Automated unit tests exercise ten skipped updates and
  corrupt/truncated patches.
- A real Android 15 device with BLINK's production signing key must also be
  verified before promotion: no data lost, same account/session, rollback
  prevention, low storage, invalid patch, pause/resume and full fallback.
- This is APK distribution only. Google Play uses Play in-app updates. Windows
  continues its Windows-native distribution/updating route.

## Production control

Work on a Testlab feature branch first. Required Android quality, Windows,
APK security, publisher audits and Android 15 emulator checks must pass for the
final SHA. Main promotion requires owner SHA-specific approval. Do not
publish an unverified Testlab build as production.
