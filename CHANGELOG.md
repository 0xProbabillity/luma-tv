# Changelog

## 0.4.6: Weather for any city (from Luma 2.5)
- **Settings, Weather location**: Automatic (the main city of the TV's time zone, as before, with no request) or any city you search for.
  Results come as you type, Search on the keyboard jumps to the first, names in other alphabets are found in their own language, and only
  towns and cities are listed, those called what you typed first. The search runs only when you type, needs Weather to be on, and sends only
  what you typed to `geocoding-api.open-meteo.com` (the second host of the network allow-list).
- Automatic Celsius/Fahrenheit follows the country of the weather's city. The window no longer pans under the keyboard.
- Not taken from Luma 2.5: its automatic city from the TV's internet address (it would send the TV's address to GeoJS and ipwho.is). See
  `NOTICE.md`.
- `SettingsScreen` shrinks again: the temperature and location rows moved to `WeatherSettings`. Tests: 70.

## 0.4.5: Grab to move, Uninstall, and your own photo
- **Grab to move**: hold OK on a tile and it lifts; move it with the d-pad and press OK to put it down (Back puts it down where it is). It
  works on Home and on the Apps page, and hidden or uninstalled apps in between never make a move a no-op. Put down without moving it,
  the tile's menu opens as before.
- **Uninstall** in the menu of an app you installed (not for the TV's own apps); Android shows its own confirmation. The freed menu space is
  from dropping "Move left" and "Move right". This needs the normal `REQUEST_DELETE_PACKAGES` permission, listed on the privacy page and in
  the audit.
- **Your photo** as wallpaper: `./shangos.sh wallpaper ~/photo.jpg` hands a picture to the app's private storage over adb. No media
  permission, no account, no network. It becomes the look "Your photo", and the screen saver shows it dimmed. `--remove` deletes it. The
  receiving provider is exported but protected by the DUMP permission, which only the adb shell holds, and the audit checks that.
- Docs: the guide no longer mentions a Menu key that did nothing.

## 0.4.4: Launch as a departure
- Opening an app from a tile now grows its window out of the card (the system draws it, nothing runs in shangOS meanwhile). With Fluid motion
  off it simply appears, as before.

## 0.4.3: The hero knows what you were doing, and the room follows your focus
- **Resume hero**: focus an app that has something to continue and the line under its name reads "Continue: Stranger Things · S4 E3 ·
  18 min left", with the programme's picture at the right. Apps with nothing to resume still say when you last opened them. It uses the
  Continue watching list you already allowed for Up Next (and the same remote-poster setting for pictures); nothing new is requested.
- **Room lighting**: the light behind your apps takes the colour of the app you are on (Netflix warms to red, Spotify to green) and the
  wallpaper dims a little while focus is on the apps. Two cross-fading gradients, nothing per frame. Settings, Room lighting turns it off;
  it is also off on the Light performance setting.
- Under the hood: `AppLauncher` (opening apps and the long-press menu), `WatchNext`/`Resume`/`Posters` (Up Next now shares them),
  `StageLight`. Tests: 60.

## 0.4.2: Return where you were, and tell the truth
- **Return where you were**: open an app and come back (Home or Back) and focus is on that app's tile, not on YouTube. The hero reads
  "Opened just now". Pressing Home while on Home still returns to the first app and the greeting.
- **The hero stays true**: "Opened 2 minutes ago" counts up while you sit on the tile (checked at the start of every minute), and the
  greeting's date follows midnight.
- **Clock and date follow the TV**: the Home clock uses the TV's 12/24-hour setting; the date uses the TV's language. The screen saver
  shares the same code, so all three agree.
- Copy: Settings said "Silver" and "silver silk to the Milky Way"; the default wallpaper is Daybreak and the list runs from Daybreak to
  Calm Sea. README status updated.

## 0.4.1: MainActivity split (no behaviour change)
- `MainActivity` went from 2,334 to under 800 lines. Each job now has its own class: `AppList` (order, hiding and recents, unit tested),
  `AppPages`, `AppTile`, `Hero`, `Chrome` (tabs and clock), `SettingsScreen`, `WallpaperPicker`, `NameEditor`, `HomeScreen`, `Feedback`,
  `Performance` and `DaylightSync`, each with one narrow `Host` interface instead of the whole Activity.
- `tools/lint.sh` now fails if `MainActivity` grows past 800 lines or any other non-widget class past 700.
- Tests: 54 (adds `AppList`).
- Removed code that was written but never read.

## 0.4.0: Home that is shangOS's own
- **Adaptive tiles**: every app gets the same glass card tinted from its own icon (dominant colour, weighted by saturation), with its banner or its icon on a
  soft light. The hand-drawn art for six named apps and the per-app colour and tagline tables inherited from Luma are gone.
- **Honest hero**: the line under the focused app is real information ("Opened 2 hours ago", from the times shangOS recorded), not marketing copy.
- **shangOS Ambient screen saver**: a large clock and date over a dimmed copy of your wallpaper, moving to a new place every minute (no pixel stays lit).
  No network, no permission. Settings has a Screen saver row; `shangos.sh ambient on|off|preview` sets it on TVs that hide the choice, and removing
  shangOS puts the previous screen saver back first.
- **Setup guide**: new "Pick a look" step (6 steps now); the wallpaper shows through while you choose.
- Audit rule: every exported service must require a system-only (`BIND_`) permission.
- Tests: 45 (colour extraction, relative time). Fixed a thread-ordering slip in the new accent code before release.

## 0.3.0
- **New package id `dev.madusha.shangos`** (matches shangos.madusha.dev). An install of an earlier id is a different app: remove it and install this one;
  settings do not carry over.
- **Wallpaper engine replaced.** One picture in a plain hardware view instead of continuously redrawn surface layers; movement is render-thread
  animation that stops the moment a key is pressed. The picture's memory is graphics-only. Fjord, Dusk and Graphite scenes and the water mesh are gone.
- **New wallpapers**: Daybreak (light by day, dark after sunset), Aurora, Ember, Tide, Orchid, Glacier, made for this project, plus two CC0 photos.
  Stored as 4:4:4 JPEG (WebP was measured to band visibly on dark gradients). All upstream artwork is gone.
- **Hardware check**: Settings, Performance Auto picks Light, Balanced or Quality from memory, bitness and cores; the choice and the reasons are shown.
- **Faster to appear**: the last list of apps is kept on disk, so tiles show at once on launch and refresh behind.
- **New icon and TV banner** (adaptive icon with a monochrome layer for themed icons).
- **Open-source licences** screen (Settings, About, Licences) with full texts and SPDX identifiers; About moved to its own class.
- **Setup guide**: new Performance step and a QR code to the website's default-launcher guide on the last step.
- **`shangos.sh wizard`**: lists TVs, accepts any `host:port` (remembered if you choose), picks edition, installs a local or checksum-verified release APK,
  sets the home screen, grants permissions, and a clean-up menu. Removing shangOS first gives Home back to the TV's own launcher.
- **Releases**: a `v*` tag builds, checks, signs (key only in a protected environment), verifies the certificate and reproducibility, and publishes with
  checksums and a provenance attestation. Actions are pinned to commit SHAs; CI uses the SDK already on the runner.
- **Website** draft in `docs/` (static, no third-party requests) with a brief for its builder; developer docs moved to `docs/dev/`.
- APK 4.3 MB to 2.4 MB. Tests: 35.

## 0.2.2: setup guide fixes, editor extraction, formatting
- **Setup guide**: Back now goes to the previous step instead of ending the whole guide (Back on the first step leaves it).
  Progress is saved, so leaving for a system settings page, or by mistake, resumes where it stopped; Settings shows "Step N of 4".
  A Back press that arrives just after returning from a system page is ignored, focus returns to the row you left, and when the
  system will no longer show the permission dialog the app's permission page opens instead of a dead button.
- **Overlays**: a cancel action that opens the next overlay no longer has its focus trap undone right afterwards.
- **Code**: `WidgetEditor`, `AppArt` and `Shine` moved out of `MainActivity` (2,752 to 2,238 lines after formatting); one `ScreenHost`
  interface shared by the Privacy screen and the editor instead of two copies.
- **Formatting**: all Java formatted with google-java-format (`--aosp`) and shell with shfmt, in a separate commit with no behaviour
  change (the compiled output is byte-identical). `tools/format.sh` now passes `--aosp` to match `.editorconfig`. Shellcheck findings in
  `build.sh`, `shangos.sh` and `test/run.sh` fixed, so `tools/lint.sh` passes.
- Tests: 23.

## 0.2.1: setup guide
- **Setup guide** (first run, and Settings -> Setup guide): welcome, privacy choices, optional permissions one at a time with live state,
  home-screen check. Skippable; Back counts as skip. `Overlays.valueRow` is now shared by it and the Privacy screen.
- `reportFullyDrawn()` once the first list of apps is on screen.

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
- Renamed to shangOS (`dev.madusha.shangos`); `minSdk` 31 (Android 12), `targetSdk` 34.
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
