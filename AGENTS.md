# For AI agents and new contributors

shangOS is a launcher for Android TV: plain Java, one Activity, no AndroidX, no third-party libraries, no ads, no accounts. Read
`docs/dev/ARCHITECTURE.md` (who may depend on whom) and `docs/dev/PLAN.md` (what is planned and the rules) before changing anything.

## Safety first (these have bitten real TVs)
- **Never switch off a TV's own home, launcher or setup app** (`pm disable-user` on `com.google.android.*` launchers or
  `com.google.android.tungsten.setupwraith`). On some Google TVs that leaves the TV looping at every restart. The scripts leave them on;
  `--disable-stock` is an expert option that asks first and never touches the setup app. If you find them switched off, switch them back on.
- Do not add a permission, a network host or a dependency without updating `AndroidManifest.xml`, `tools/audit.sh`, the privacy page
  (`tools/build-site.py`) and `NOTICE.md` in the same change. The default answer is no.
- Never commit addresses of real devices, keys, tokens or e-mail addresses: run `tools/privacy-scan.sh` before every commit.
- The website under `docs/` is **generated**: edit `tools/build-site.py`, run `python3 tools/build-site.py`, and commit the result
  (CI runs `--check`). Do not edit the generated HTML.

## Everyday commands
- Build: `./build.sh` (debug) or `./build.sh release`; unit tests: `test/run.sh`; style and size budget: `tools/lint.sh`; invariants:
  `tools/audit.sh`; site: `python3 tools/build-site.py --check`.
- On a TV: `./shangos.sh` (wizard) or `./shangos.sh install`, `home`, `status`, `restore`. Checklists per release: `docs/TESTING.md`.

## Rules of the code base
- One class, one job. `MainActivity` stays under 800 lines and every other non-widget class under 700 (lint fails otherwise). New
  behaviour gets its own class with a narrow `Host` interface, not a method on the Activity.
- Pure logic gets a JUnit test in `test/` (and an entry in `test/run.sh`). Anything on the main thread must not touch disk or network.
- Everything must work on the Light performance tier or be off there. No per-frame allocation, no per-frame blur, no video.
- Every claim shown to the user or written in the docs must be true: update `CHANGELOG.md`, `docs/TESTING.md` and `docs/dev/ARCHITECTURE.md`
  with the change.
- Format with `tools/format.sh`; Java 8 source level (`--release 8`); zero compiler warnings.
