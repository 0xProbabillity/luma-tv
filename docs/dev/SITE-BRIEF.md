# Website brief (for whoever builds `https://shangos.madusha.dev`)

The site is the static folder `docs/`. A working draft exists; this brief says what it is, what is missing and what must not change.

## Goal
Convince a Google TV / Android TV owner that shangOS is safe and worth a try, then get them through setup. Audience: technical enough to run `adb`, or willing to follow a guide. One page must never fail: `/setup`, because the app's QR code points to it.

## Hard constraints
- **`https://shangos.madusha.dev/setup` must exist and keep that path.** The QR code in the app (`res/drawable/qr_setup.xml`) encodes it. Changing it means regenerating the drawable and releasing an update.
- **No trackers, no analytics, no cookies, no third-party requests** (no CDN, fonts, embeds, badges). The product's promise is privacy; the site keeps it. Self-host everything.
- No JavaScript needed for any content. Works in dark and light mode (`prefers-color-scheme`), on a phone, with keyboard only, and with a screen reader. Keep contrast WCAG AA.
- Every claim about the app must be true and checkable in the source. Do not invent numbers. If you add performance claims, take them from measurements and say on what hardware.
- Stay static: plain HTML/CSS (or a generator whose output is committed). The repo deploys the folder as is.
- **The pages are generated**: edit `tools/build-site.py` (content) and `docs/assets/site.css|js`, run `python3 tools/build-site.py`, commit the output. CI runs `--check`. Features that are in the source but not in the latest release (`VERSION`) are marked with `nx("x.y.z")` and the tag disappears on its own once VERSION catches up.
- **Audience: ordinary TV owners who want no ads and no trackers.** Say it plainly; keep adb jargon out of the first screen.
- **Benchmarks** come only from `docs/data/bench.json` (written by `tools/bench.sh`), shown with the TV, versions and date, never typed into a page by hand. Comparing with Luma TV and the TV's own launcher is allowed only through that file and its method.
- **Ad/tracker blocking (Private DNS) is optional, off by default, and the page must state its limits**: the DNS provider sees lookups; apps with their own DNS skip it; some apps may break.

## What exists (`docs/`)
| Path | Content | State |
|---|---|---|
| `index.html` | Hero, four highlights, who it is for, verify | Draft: needs screenshots, a download section with the current version |
| `setup/index.html` | Enable debugging, wizard, manual commands, undo, verify, troubleshooting | Accurate; needs a pass for TV brands (Sony, TCL, Xiaomi, Chromecast with Google TV, onn) |
| `privacy/index.html` | What is sent, permissions, what is stored | Mirrors the in-app Privacy screen: keep them in step |
| `licenses/index.html` | Credits | Mirrors `Credits.java` and `NOTICE.md`: keep them in step |
| `assets/` | `site.css`, `logo.svg`, `favicon.svg` | Logo comes from the app icon geometry |
| `CNAME`, `.nojekyll` | Custom domain; serve files as they are | Do not delete |
| `dev/` | Developer docs (architecture, releasing, this brief) | Published as plain files; fine, or exclude from deployment |

## To do
1. **Screenshots** (the biggest gap): the current ones come from a release build (not a demo build). To retake them, a debug build has a demo mode with sample content:
   `adb shell am start -n dev.madusha.shangos/.MainActivity --es shangos.demo promo`, then `adb exec-out screencap -p > home.png`. Capture Home with each wallpaper, Settings, Privacy, the widget editor. Optimise to WebP/AVIF under 150 KB; provide `alt` text; no real names.
2. A **download section** that links to `https://github.com/madushas/shangos-launcher/releases/latest` and shows the signing fingerprint (take it from the release notes) next to the `sha256sum` and `apksigner` commands.
3. **Setup guide polish**: per-brand notes, a short "what will change on my TV" box, and a short screen-recording-free walkthrough (annotated screenshots of the Developer options steps).
4. A **FAQ**: why a computer is needed; is it safe; what is disabled (only the stock launcher, reversibly); what about updates (install the new APK over the old, same key); does it work on phones (no).
5. `404.html`, `robots.txt`, `sitemap.xml`, Open Graph image (1200x630, generated from the banner), `<link rel="canonical">`.
6. Accessibility and speed check: no layout shift, total transfer under 100 KB without screenshots.

## Deploying
Repository settings, Pages, "Deploy from a branch", branch `main`, folder `/docs`. Custom domain `shangos.madusha.dev`, enforce HTTPS. DNS: a `CNAME` record for `shangos` pointing to `madushas.github.io`. Open `https://shangos.madusha.dev/setup/` afterwards and scan the QR code from the app (Settings, Setup guide, last step) to confirm.

## Tone
Plain and honest. Say what it does and what it does not. No superlatives, no "revolutionary". Comparisons with named products only from the measured `bench.json`. It is a hobby project: say so.

## Brand (Material You tonal slate green)
Icon and banner: a dark slate-green tile (`#40552e` to `#27341d`), a pale sage play button (`#e6f4c6` to `#c3dc9d`) and "os" in deep green (`#1c2911`).
Website: light theme `#f9faef` surfaces with `#4c662b` (buttons, links; 6.2 to 6.5:1) and `#cdeda3` / `#354e16` icon tiles; dark theme `#12140e` surfaces with `#b1d18a` accents (10:1) and `#354e16` / `#cdeda3` tiles. All are tokens at the top of `site.css`.
One artwork feeds everything: `python3 tools/brand-art.py` writes `docs/assets/logo.svg`, `favicon.svg` and `banner.svg`; `python3 tools/make-brand.py` turns those into the app's Android drawables (`res/drawable/app_icon_*.xml`, `app_banner.xml`). `apple-touch-icon.png` (180 px) and `og.jpg` (1200x630, banner on dark slate with the tagline) are rendered from the SVGs by hand when the artwork changes.
