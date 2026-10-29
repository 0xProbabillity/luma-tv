# Architecture

Plain Java, no libraries, one process, one Activity. Package `com.madushas.shangos`, folder `src/com/madushas/shangos`.
Layout is in "design units" (1/960 of the screen width) so one design fits any TV.

| Class | Responsibility | May depend on |
|---|---|---|
| `MainActivity` | Lifecycle, Home and Settings pages, tiles, focus and key handling, widget editor, pickers | everything below |
| `Prefs` | Every saved setting: typed accessors, defaults, schema version, migrations. **Only** class with key names | Android prefs |
| `Net` | **Only** class that opens connections. Rules: https, port 443, public addresses, size/type caps, host-locked JSON | `Prefs`, `Flavor` |
| `AppRepo`, `App`, `AppMeta` | Installed apps: change-driven discovery, lazy pictures, byte-bounded caches, memory trim. **Only** class that registers receivers | `Dbg` |
| `Wallpaper` | Sky/terrain/photo surfaces on their own thread, parallax, ambient drift, idle pause, release on trim | `Host` interface, `Motion`, `Ui` |
| `Overlays` | Menus, notices, full-screen panels, focus trapping | `Host` interface, `Ui`, `Theme` |
| `PrivacyScreen` | About and Privacy screens | `Overlays`, `Prefs`, `Net` |
| `Widget`, `WidgetBoard`, `*Widget` | Home widgets and their pages | `Prefs`, `Net`, `Ui`, `Motion` |
| `Motion`, `Spring`, `Theme`, `Ui` | Curves, colour tokens, view helpers | Android only |
| `Images`, `Dbg`, `Flavor` (generated) | Safe decoding, debug-only logging, build flags | Android only |

The local policy checks enforce: sockets only in `Net`; preferences only in `Prefs`; receivers only in `AppRepo`; no
deprecated API; no debug hooks outside `Flavor.DEBUG`; per-variant permission lists.

Why not `LauncherApps` for the app list: it only lists CATEGORY_LAUNCHER activities, and many TV apps declare only
CATEGORY_LEANBACK_LAUNCHER. Why not AndroidX: no feature here needs it, and every library is code to audit and ship.

## Known structural debt (planned, Phase 3)
- `MainActivity` still holds the Settings page, widget editor, tile class and the name/wallpaper pickers (about 960 lines).
- `Art` (the tile painter) hard-codes drawings for six named apps from the upstream project.
- Most upstream code is formatted as dense one-liners; new code is not. No formatter is wired in yet.
- Strings are literals in code; they move to resources with the translations work.
- Android-dependent classes (`AppRepo`, `Wallpaper`) have no automated tests; only logic that runs off-device does.
