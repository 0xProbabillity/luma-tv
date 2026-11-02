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
1. **Screenshots** (the biggest gap): use a debug build, which has a demo mode with attractive sample content:
   `adb shell am start -n dev.madusha.shangos/.MainActivity --es shangos.demo promo`, then `adb exec-out screencap -p > home.png`. Capture Home with each wallpaper, Settings, Privacy, the widget editor. Optimise to WebP/AVIF under 150 KB; provide `alt` text; no real names.
2. A **download section** that links to `https://github.com/madushas/shangos-launcher/releases/latest` and shows the signing fingerprint (take it from the release notes) next to the `sha256sum` and `apksigner` commands.
3. **Setup guide polish**: per-brand notes, a short "what will change on my TV" box, and a short screen-recording-free walkthrough (annotated screenshots of the Developer options steps).
4. A **FAQ**: why a computer is needed; is it safe; what is disabled (only the stock launcher, reversibly); what about updates (install the new APK over the old, same key); does it work on phones (no).
5. `404.html`, `robots.txt`, `sitemap.xml`, Open Graph image (1200x630, generated from the banner), `<link rel="canonical">`.
6. Accessibility and speed check: no layout shift, total transfer under 100 KB without screenshots.

## Deploying
Repository settings, Pages, "Deploy from a branch", branch `main`, folder `/docs`. Custom domain `shangos.madusha.dev`, enforce HTTPS. DNS: a `CNAME` record for `shangos` pointing to `madushas.github.io`. Open `https://shangos.madusha.dev/setup/` afterwards and scan the QR code from the app (Settings, Setup guide, last step) to confirm.

## Tone
Plain and honest. Say what it does and what it does not. No superlatives, no "revolutionary", no comparison tables against named products. It is a hobby project: say so.
