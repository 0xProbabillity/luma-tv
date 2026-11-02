# Releasing

A release is a git tag. Pushing `v0.3.0` makes GitHub Actions build, sign and publish it (`.github/workflows/release.yml`).
The signing key never leaves GitHub: it is stored as secrets of a protected environment, and the workflow refuses to publish
anything signed by a different key.

## One-time setup

1. **Create the signing key**, once, on your own computer. This key *is* the app's identity: Android only installs updates signed by the
   same key, so back it up (offline, twice) and never commit it.
   ```
   keytool -genkeypair -keystore shangos-release.jks -alias shangos -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=shangOS release, O=madushas"
   ```
2. **Record its fingerprint** (the workflow compares against this, and users compare against it):
   ```
   keytool -list -v -keystore shangos-release.jks -alias shangos | grep SHA256:
   ```
3. **In the repository settings**
   - Environments, New environment `release`. Tick *Required reviewers* and add yourself: nothing is signed until you approve each release.
   - In that environment add secrets: `SHANGOS_KEYSTORE_B64` (`base64 -w0 shangos-release.jks`), `SHANGOS_KS_PASS` (the password),
     `SHANGOS_KEY_ALIAS` (`shangos`).
   - Add a repository *variable* (not a secret) `SHANGOS_CERT_SHA256` with the fingerprint from step 2. A build signed with any other key fails.
   - Optionally protect `main` (pull requests, the `build` check required) and add a tag rule so only maintainers can create `v*` tags.
4. Publish the fingerprint where users can find it: the website's download page and the release notes (the workflow adds it to the notes).

## Making a release

1. Update `VERSION` (`versionName` and a higher `versionCode`) and add a `## X.Y.Z` section to `CHANGELOG.md`. The section becomes the release notes.
2. Merge to `main` through a pull request; wait for the `build` check.
3. Tag and push:
   ```
   git tag v0.3.0 && git push origin v0.3.0
   ```
   A tag with a hyphen (`v0.3.0-rc1`) is published as a pre-release. The tag must equal `v` plus `versionName`, and the commit must be on `main`.
4. Approve the run in the Actions tab. It runs the checks, builds the `release` and `offline` APKs, signs them, checks the certificate, builds
   again to prove the result is reproducible, writes `SHA256SUMS`, attaches a build-provenance attestation and creates the release.
5. Verify the published APK yourself once: `sha256sum -c SHA256SUMS`, and `apksigner verify --print-certs` shows the expected fingerprint.

## Before the first public release

- The website is live at the address the in-app QR code points to (`https://shangos.madusha.dev/setup`). The code is generated from that
  address; if it ever changes, the QR drawable must be regenerated.
- A test install from the release APK works over the previous one (same key), and `shangos.sh wizard` can download and verify it.

## If the key is lost or leaked

A lost key means existing installs cannot be updated: users must uninstall and install a new build under a new key. A leaked key means anyone can sign
an APK that Android will accept as an update. Revoke by rotating: create a new key, publish its fingerprint, and ask users to reinstall.
