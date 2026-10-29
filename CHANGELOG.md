# Changelog

## 0.2.0 — Phase 2: speed, memory and structure (not yet tested on a device)
Renamed to shangOS (`com.madushas.shangos`).
**Performance** (each item names the cost it removes; validate on a real device)
- App list is read only when a package changes (receiver, debounced), not on every return to Home. Before, every return
  re-queried all apps and decoded every icon and banner, then discarded them if nothing had changed.
- Icons and banners decode on first draw, off the main thread, into byte-bounded LRU caches (2 MB / 3 MB, half on
  low-RAM TVs) that shrink on memory pressure. A failed icon load is remembered so it cannot loop.
- Wallpaper: replaced pictures are recycled once faded out (they waited for GC); the Fjord source images are freed after
  composing (about 12 MB); stale picker previews are skipped instead of decoded; the draw thread reads each bitmap once
  (fixes a race with the UI thread); everything is released at the system's background trim level and restored on return.
- Ambient wallpaper pauses after 10 minutes without a key press (30 on Quality) and resumes on the next one.
- New Settings row: Performance (Auto / Quality / Light). Light keeps the wallpaper still; Auto chooses by the TV's RAM.
- Fjord PNGs converted to lossless WebP (pixel-identical, measured): APK 5.0 MB to 4.3 MB.
- Art no longer allocates Paths while drawing.
**Code**
- Split MainActivity (1,388 to 965 lines): Motion, Spring, Theme, Ui, Prefs, App, AppMeta, AppRepo, Wallpaper, Overlays,
  PrivacyScreen. One spring implementation instead of three, one set of view helpers instead of two.
- Prefs is the only class that knows preference keys; it has a schema version and migrations.
- Modern APIs, zero compiler deprecation warnings: WindowInsetsController, OnBackInvokedCallback (API 33+) with the legacy
  path on 31-32, ActivityOptions instead of overridePendingTransition, unsafeCheckOpNoThrow, Activity.getDisplay.
- Fixed: a user-visible string damaged by my own rename ("Clouds Motion.DRIFT"), caught before release.
- Tests: 21 (Net, Spring against the original formula, Prefs including migration). Audit gained structure rules.

## 0.1.0 — Phase 1: security and privacy core (not yet tested on a device)
Forked from Luma TV 2.4.
- Renamed to shangOS (`com.madushas.shangos`); `minSdk` 31 (Android 12), `targetSdk` 34.
- **New `Net`**: the only class that opens connections. https only, port 443, no credentials in URLs, every
  resolved address must be public (checked again on each redirect, max 2), content-type and size caps, one
  generic User-Agent. Weather JSON is locked to `api.open-meteo.com`. 11 unit tests plus a live check.
- Up Next posters and Now Playing art now go through `Net`; `file:` and `http:` links are refused; pictures are
  decoded sampled and refused above 16 megapixels.
- Cleartext traffic disabled (network security config, no exceptions).
- **Removed House Control**: Binder client, WebView, localhost server, scene logic (about 400 lines).
- **Weather is opt-in** (off by default) and its disclosure is shown where you turn it on.
- Remote posters setting: Off / Wi-Fi and Ethernet only (default) / Always. **Offline mode** switch.
- **Privacy screen**: network switch, opt-ins, live permission states, signing-certificate fingerprint.
- Demo/QA intent extras work only in the debug build (the activity is exported).
- Dropped `ACCESS_WIFI_STATE` (signal strength now comes from the connection itself).
- Build variants: `debug`, `release`, `offline` (no INTERNET permission at all).
- Removed the author's donation content, unused 1.9 MB image, House Control `<queries>` entry.
- Added local policy and device-measurement tooling, plus `test/` (JUnit).
