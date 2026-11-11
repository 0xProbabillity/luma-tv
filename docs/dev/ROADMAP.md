# Roadmap

## Why shangOS is not "Luma with new colours" (and what is still too close)
Already different: no network by default and provably so, a plain-view wallpaper engine, a hardware check, signed reproducible releases, a guided
setup script, licences, a website. **Still too close to Luma, and the focus of 0.4:** the Home screen's look and copy. Tiles are hand-drawn for six
named apps, each app's colour and tagline is a hard-coded table, and the line under the focused tile is marketing copy ("Great stories. Even better
evenings."). A launcher should say something *true*.

## Principles
1. Calm: nothing moves, speaks or sells unless the user asked.
2. Honest: every line on screen is real information.
3. Yours: look, layout and behaviour are the user's to change, from the TV, with a remote.
4. Fast on the worst hardware we support (1.4 GB, 32-bit, 720p); richer on better.
5. Private and verifiable. Never: ads, accounts, analytics, cloud.

## 0.4: Make Home shangOS (done)
- **Adaptive tiles**: every app gets the same glass card tinted from its own icon; no per-app drawings or colour tables.
- **Honest hero**: the line under the focused app is real ("Opened 2 hours ago"), not copy.
- **Ambient screen saver**: a quiet clock over your wallpaper, drifting to protect the screen; no network, no permissions; set from the wizard.
- **Setup: choose your look** (wallpaper) in the first-run guide.

## 0.4.1 to 0.4.5: Feel (plan in REFACTOR.md)
- 0.4.1: MainActivity split into 7 no-behaviour slices (done, under 800 lines), size budget in lint.
- 0.4.2: return to where you were; "tell the truth" pass (Daybreak copy, locale clock, live hero minute).
- 0.4.3: resume hero (per-app Watch Next, poster); the focused app tints the room.
- 0.4.4: launch grows out of the tile.
- 0.4.5: grab-to-move on tiles, Uninstall in the menu; your own photo as wallpaper (no permission).

## 0.5: Rows and layout
- Home rows you choose and order: Favourites, Recent, All apps; density (compact / standard); tile size.
- Folders / categories (Streaming, Games, Tools); drag-free reordering with the remote.
- Launch an app or an HDMI input at boot; HDMI input shortcuts as tiles.
- Quick panel (long-press OK on the clock): wallpaper, performance, offline mode, screen saver.

## 0.6: Reach
- Search across apps and settings (voice via the system recognizer where present; no typing marathon with a D-pad).
- Profiles: separate favourites and wallpaper per person.
- Translations (strings to resources), right-to-left check, TalkBack audit of every screen.
- Back up and restore settings as one local file.

## 0.7: Polish and trust
- Clock and greeting styles; accent themes; reduced-motion and large-text modes tested on hardware.
- Reproducible-build verification instructions; F-Droid-style metadata.
- A published threat model and a test suite for every privacy claim on the website.

## 1.0 bar
Two weeks of daily use on three different TVs without a crash or a regression; the website live; releases signed and verifiable; documented upgrade path.

## Not planned
Ads or sponsored rows, accounts, analytics or crash reporting, cloud sync, a store, content recommendations from a server.
