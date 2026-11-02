# Architecture

Plain Java, no libraries, one process, one Activity. Package `dev.madusha.shangos`, folder `src/dev/madusha/shangos`.
Layout is in "design units" (1/960 of the screen width) so one design fits any TV.

| Class | Responsibility | May depend on |
|---|---|---|
| `MainActivity` | Lifecycle, Home and Settings pages, tiles, focus and key handling, name and wallpaper pickers | everything below |
| `Prefs` | Every saved setting: typed accessors, defaults, schema version, migrations. **Only** class with key names | Android prefs |
| `Net` | **Only** class that opens connections. Rules: https, port 443, public addresses, size/type caps, host-locked JSON | `Prefs`, `Flavor` |
| `AppRepo`, `App`, `AppMeta`, `AppCache` | Installed apps: change-driven discovery, last list kept on disk so Home appears at once, lazy pictures, byte-bounded caches, memory trim. **Only** class that registers receivers | `Dbg` |
| `Wallpaper` | One picture in a plain hardware view (GPU-only bitmap), crossfade, parallax, slow glides of light on the render thread (stop on any key, pause when idle), release on trim | `Host` interface, `Hardware`, `Motion`, `Ui` |
| `Hardware` | Pure hardware classification (memory, bitness, cores) into Light / Balanced / Quality; `detect` only gathers the facts | Android only |
| `Overlays` | Menus, notices, full-screen panels, focus trapping and restoring | `Host` interface, `Ui`, `Theme` |
| `ScreenHost` | The five callbacks full-screen panels need (motion, sounds, post, back to Settings) | Android only |
| `PrivacyScreen` | The Privacy screen | `Overlays`, `ScreenHost`, `Prefs`, `Net` |
| `AboutScreen`, `LicensesScreen`, `Credits` | About, and the open-source licences with full texts (data in `Credits`, kept in step with `NOTICE.md`) | `Overlays`, `ScreenHost` |
| `SetupGuide` | First-run guide: welcome, performance, privacy, optional permissions, home screen with a QR code to the website; remembers its step | `Overlays`, `PrivacyScreen`, `Hardware`, `Prefs` |
| `WidgetEditor` | Settings, Home widgets: toggle, reorder, live miniature pages | `Overlays`, `ScreenHost`, `Prefs`, `WidgetBoard` |
| `AppArt`, `Shine` | A tile's picture (banner or icon on its colour) and its focus highlight | `AppRepo`, `Ui` |
| `Widget`, `WidgetBoard`, `*Widget` | Home widgets and their pages | `Prefs`, `Net`, `Ui`, `Motion` |
| `Motion`, `Spring`, `Theme`, `Ui` | Curves, colour tokens, view helpers | Android only |
| `Images`, `Dbg`, `Flavor` (generated) | Safe decoding, debug-only logging, build flags | Android only |

The local policy checks enforce: sockets only in `Net`; preferences only in `Prefs`; receivers only in `AppRepo`; no
deprecated API; no debug hooks outside `Flavor.DEBUG`; per-variant permission lists.

Why not `LauncherApps` for the app list: it only lists CATEGORY_LAUNCHER activities, and many TV apps declare only
CATEGORY_LEANBACK_LAUNCHER. Why not AndroidX: no feature here needs it, and every library is code to audit and ship.

## Wallpaper design in one paragraph
A wallpaper that moves by about one pixel per second must not be redrawn ten times a second. The picture is a texture in an ordinary view; all
movement is a property animation the render thread runs without the UI thread. Glides come in bursts with pauses, never run while the user is
pressing keys, and are off entirely on the Light tier. The previous engine (separate surface layers redrawn continuously) cost 4% of a core
while idle and slowed focus animations on a small stick.

## Known structural debt
- `MainActivity` (about 2,200 lines once formatted) still holds the Settings page, the tile classes and the name/wallpaper pickers.
- `AppArt` (the tile painter) hard-codes drawings for six named apps from the upstream project.
- The website in `docs/` mirrors the Privacy and Licences screens by hand; they must be kept in step.
- Code style is enforced by `tools/format.sh` (google-java-format `--aosp`, shfmt) and `tools/lint.sh`.
- Strings are literals in code; they move to resources with the translations work.
- Android-dependent classes (`AppRepo`, `Wallpaper`) have no automated tests; only logic that runs off-device does.
