# Refactor of MainActivity (0.4.1, done) and the plan for the "Feel" releases (0.4.2 to 0.4.5)

Policy kept: plain Java, one Activity, no AndroidX, no Fragments, no Compose (see ARCHITECTURE.md). Every
extracted class gets one narrow `Host` interface, never the whole Activity.

## Rules
1. One slice = one commit = no behaviour change. Update the ARCHITECTURE.md table in the same commit.
2. Features land only after the slice they need. Never mix a move and a behaviour change in one diff.
3. Lower `MAIN_MAX` in `tools/lint.sh` after every slice (ratchet; never raise it).
4. Pure logic gets JUnit tests in the same commit and is added to `test/run.sh`.
5. Slices that move Views are checked on a TV with `docs/TESTING.md`: focus order, Back, return from an app.

## Slices (MainActivity: 2,334 lines before, 792 after)
| # | Slice | New classes | Host (narrow) | `MAIN_MAX` | Status |
|---|---|---|---|---|---|
| 1 | Order, visibility, move/hide, recents | `AppList` (pure, 13 tests) | none | 2400 | **done** |
| 2 | Tile and TileMotion inner classes | `AppTile`, `TileMotion` | `AppTile.Host` | 2150 | **done** |
| 3 | Hero: crossfade, greeting, date, recency line | `Hero` | `Hero.Host` | 2000 | **done** |
| 4 | Settings page and its row framework | `SettingsScreen` | `SettingsScreen.Host` + shared `ScreenHost` | 1400 | **done** |
| 5 | Wallpaper picker, name editor, home-screen switch | `WallpaperPicker`, `NameEditor`, `HomeScreen` | one small `Host` each + `ScreenHost` | 1100 | **done** |
| 6 | Tabs, sliding pill, clock, shade | `Chrome` | `Chrome.Host` | 950 | **done** |
| 7 | Home/Apps builders, feel, performance, daylight | `AppPages`, `Feedback`, `Performance`, `DaylightSync` | `AppPages.Host`, `DaylightSync.Host` | **800** | **done** |

Left in `MainActivity` on purpose: lifecycle, page switching, Back and key policy, `launch`, the app menu, and the
`Host` adapters (kept as separate narrow interfaces; one fat `ActivityHost` would only move the god class).

Dead code removed on the way (it was written but never read): `homeGreeting`, `heroPackage`, `greeting()`,
`brand` (an invisible wordmark view), `focusNearestTile`, `appLabel`.

Slice notes worth keeping:
- `AppTile.COLUMNS` is now the only place the grid width (6) lives, so the optional 2-row Home is a small change.
- `AppPages.select(pkg)` / `selectDefault()` own the selected app. "Return where you were" (0.4.2) is a change to the
  `selectDefault()` calls in `onResume` and `onNewIntent`, nothing else.
- `Hero.select/widget/showGreeting` are the only ways the hero changes; the per-app resume line (0.4.3) goes in
  `Hero.select`, and the minute refresh (0.4.2) in `Hero` too.
- `Chrome.startClock()` is where the locale and 12/24-hour fix (0.4.2) goes.
- `Wallpaper.clamp` replaced the copies of the "keep the saved wallpaper number in range" expression.

## Feature plan (from future_plan.md), in dependency order
| Release | Feature | Needs slice | Size | Why this order |
|---|---|---|---|---|
| 0.4.2 | **done** 3. Return where you were: keep `selectedPackage` = last launched app, do not reset or scroll in `onResume`; hero shows "Opened just now" | 1, 2 | S | Smallest, fixes a real feel bug; `AppList.remember` + `AppTile` make it a few lines |
| 0.4.2 | **done** 7. Tell the truth pass: "Silver" copy to Daybreak (Settings, comments, MEASURING.md); Home clock and date follow locale and 12/24h (`DateFormat.is24HourFormat`); README status; hero refreshes every 60 s | 3 | S | Pure copy/formatting; the minute refresh lives in `Hero` |
| 0.4.3 | **done** 2. Resume hero: per-app Watch Next ("Continue: S4 E3 · 18 min left", poster at right); fall back to recency | 3 | M | Needs `Hero`; add `WatchNext` (query by `package_name`, off-main-thread) and stop merging rows in `UpNextWidget` so one query serves both |
| 0.4.3 | **done** 1. Room tint (stage lighting): `Wallpaper` glow takes the focused app's `Tint` colour; dim a touch when focus is on the app row | 2 | S/M | Recolour the existing gradient once per focus (no per-frame work); off on the Light tier |
| 0.4.4 | **done** 4. Launch as departure: `ActivityOptions.makeScaleUpAnimation(tile, ...)` replaces `Motion.instant`; if it stutters on the stick, change the Settings copy instead | 2 | S | `AppTile` exposes its own bounds; needs measuring on 1.4 GB hardware |
| 0.4.5 | **done** 5. Grab to move: hold OK lifts the tile, d-pad moves, OK drops (reuse the widget editor's pattern); hold without moving opens the menu; menu gains Uninstall | 1, 2 | M | `AppList.moveLeft/moveRight` already exist and are tested |
| 0.4.5 | **done** 6. Your photo wallpaper: `shangos.sh wallpaper ~/photo.jpg` to private storage; "Your photo" look; screensaver reuses it dimmed | 4, 5 | M | Lives in `WallpaperPicker`; no permission, no network |
| 0.5 | Rows you choose, 2-row Home, high-contrast mode, manual page breaks | 1 to 7 | L | 0.5 roadmap items; all land on seams created above |

Item numbers match future_plan.md. "2-row Home" = optional setting, hint on Home that the Apps tab has the rest.

## Per-feature acceptance checks
- Return where you were: open Netflix, Home: focus is still on Netflix, no scroll, hero says "Opened just now".
- Resume hero: with Watch Next access granted, a resumable app shows title, episode and time left; without access, recency.
- Room tint: no per-frame allocation (check with the idle CPU baseline in `tools/baseline.sh`); Light tier unchanged.
- Launch animation: `tools/baseline.sh` per-launch fully-drawn time must not regress vs 0.4.0.
- Grab to move: hold OK without moving still opens the menu; Back cancels the grab and restores the order.
