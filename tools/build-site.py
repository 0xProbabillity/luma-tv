#!/usr/bin/env python3
"""Builds docs/**/*.html (the public website) from the content below.

    python3 tools/build-site.py

The output is plain HTML and is committed; nothing at runtime depends on this script. The shared
header, footer and head live in shell(); each page is a function. Keep claims in step with the
app: privacy page = Settings, Privacy; licenses page = Credits.java and NOTICE.md.
"""
import html
import json
import pathlib
import re
import sys

DOCS = pathlib.Path(__file__).resolve().parent.parent / "docs"
SITE = "https://shangos.madusha.dev"
REPO = "https://github.com/madushas/shangos-launcher"
LATEST = REPO + "/releases/latest"

CMD_SH = "curl -fsSL https://shangos.madusha.dev/setup.sh | bash"
CMD_PS = ("[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; "
          "iex ((New-Object Net.WebClient).DownloadString('https://shangos.madusha.dev/setup.ps1'))")
PS_FETCH = "(New-Object Net.WebClient).DownloadString('https://shangos.madusha.dev/setup.ps1')"
CMD_PS_ARGS = "& ([scriptblock]::Create(" + PS_FETCH + "))"

e = html.escape

ROOT = DOCS.parent
RELEASED = re.search(r"versionName=(\S+)", (ROOT / "VERSION").read_text()).group(1)  # the latest release (VERSION)


def _vt(v):
    return tuple(int(x) for x in re.findall(r"\d+", v)[:3])


def nx(v):
    """A small tag for something that is in the source but not in the latest release yet. It disappears once VERSION reaches v."""
    return f' <span class="tag" title="Added in {v}; the latest release is {RELEASED}">In {v}</span>' if _vt(v) > _vt(RELEASED) else ""


def load_bench():
    f = DOCS / "data" / "bench.json"
    return json.loads(f.read_text(encoding="utf-8")) if f.exists() else None


BENCH_ROWS = [  # (highlight key, label, unit); lower is better in every row
    ("memory_idle_mb", "Memory in use, idle on Home", "MB"), ("memory_after_returns_mb", "Memory after 10 trips back to Home", "MB"),
    ("first_frame_ms", "Time to first frame after a restart (median of 8)", "ms"), ("return_to_home_ms", "Trip back to Home", "ms"),
    ("cpu_idle_percent_of_core", "Processor use while idle on Home", "% of one core"), ("janky_frames_percent", "Janky frames while moving around", "%"),
]


def bench_section():
    """The measured summary, built from docs/data/bench.json (written by tools/bench.sh; the release build checks and publishes it)."""
    b = load_bench()
    if not b or not b.get("results"):
        return ""
    res = b["results"]
    cols = [f'{r["name"]} {r["version"]}' + (f' ({r["variant"]})' if r.get("variant") else "") for r in res]
    rows = []
    for k, label, unit in BENCH_ROWS:
        if not any(k in r["highlights"] for r in res):
            continue
        rows.append([label] + [(f'{r["highlights"][k]:,} {unit}' if k in r["highlights"] else "not measured") for r in res])
    mine = next((r for r in res if r["name"] == "shangOS"), None)
    notes = []
    if mine:
        for o in (r for r in res if r is not mine):
            a, c = mine["highlights"].get("memory_after_returns_mb"), o["highlights"].get("memory_after_returns_mb")
            if a and c:
                pct = round(abs(c - a) / c * 100)
                notes.append(f'After ten trips back to Home, {mine["name"]} {mine["version"]} held {a:g}&nbsp;MB and {o["name"]} {o["version"]} held {c:g}&nbsp;MB ({pct}% {"less" if a < c else "more"}).')
            a, c = mine["highlights"].get("first_frame_ms"), o["highlights"].get("first_frame_ms")
            if a and c:
                notes.append("Start-up time was about the same." if abs(a - c) / max(a, c) < 0.1 else f'Start-up took {a:g}&nbsp;ms against {c:g}&nbsp;ms.')
    stale = [f'{r["name"]} {r["version"]}' for r in res if r["name"] == "shangOS" and r["version"] != RELEASED]
    day = (b.get("generated") or "")[:10]
    dev = b["device"]
    ram = f'{dev["memory_kb"] / 1048576:.1f}&nbsp;GB RAM, ' if dev.get("memory_kb") else ""
    fine = (f'Measured {e(day)} on one TV: {e(dev["description"])} ({ram}{e(dev.get("display", ""))}). '
            + (f'The current release is {RELEASED}; {e(stale[0])} was measured, so newer versions have not been measured yet. ' if stale else "")
            + f'One TV on one day is a guide, not a promise; differences under about 10% are noise. Memory is Android&rsquo;s own figure (PSS), in MB of 1,024&nbsp;kB. The method is <a href="{REPO}/blob/main/docs/MEASURING.md" rel="noopener">written down</a>, '
            f'the raw numbers are in <a href="/data/bench.json">bench.json</a>, and <code>tools/bench.sh</code> measures your own TV.')
    return f"""
<section class="section section--sunk" aria-labelledby="num-h">
  <div class="wrap">
    <div class="section__head"><span class="kicker">Measured</span><h2 id="num-h">How light is it?</h2><p>{" ".join(notes) or "Numbers from one small TV stick."}</p></div>
    {table(["Measure"] + [e(c) for c in cols], rows, caption="Lower is better in every row")}
    <p class="fine" style="margin-top:var(--s-4)">{fine}</p>
  </div>
</section>"""

ICONS = {
    "sun": '<path d="M12 3v2M12 19v2M5 12H3M21 12h-2M6.3 6.3 4.9 4.9M19.1 19.1l-1.4-1.4M6.3 17.7l-1.4 1.4M19.1 4.9l-1.4 1.4"/><circle cx="12" cy="12" r="4"/>',
    "moon": '<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/>',
    "menu": '<path d="M4 7h16M4 12h16M4 17h16"/>',
    "home": '<path d="M4 11 12 4l8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"/>',
    "grid": '<rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>',
    "image": '<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="m4 18 5-5 4 4 3-3 4 4"/>',
    "shield": '<path d="M12 3 5 6v5c0 4.5 3 8 7 10 4-2 7-5.5 7-10V6z"/><path d="m9 12 2 2 4-4"/>',
    "undo": '<path d="M9 7H5v4"/><path d="M5 11a8 8 0 1 1 2 6"/>',
    "lock": '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    "gauge": '<path d="M4 16a8 8 0 1 1 16 0"/><path d="m12 16 4-5"/>',
}


def icon(name, cls=""):
    return f'<svg viewBox="0 0 24 24" aria-hidden="true"{(" class=" + chr(34) + cls + chr(34)) if cls else ""}>{ICONS[name]}</svg>'


def term(lines, label="Terminal", copy=True):
    """lines: list of (kind, text). kind: p = command (copied), c = comment, o = output, '' = continuation."""
    out = []
    for kind, text in lines:
        if kind == "p":
            out.append(f'<span class="p">$ </span>{e(text)}')
        elif kind == "w":
            out.append(f'<span class="p">PS&gt; </span>{e(text)}')
        elif kind == "c":
            out.append(f'<span class="c">{e(text)}</span>')
        elif kind == "o":
            out.append(f'<span class="o">{e(text)}</span>')
        else:
            out.append(e(text))
    btn = '<button class="copy" type="button">Copy</button>' if copy else ""
    return (f'<div class="term"><div class="term__bar"><span>{e(label)}</span>{btn}</div>'
            f'<pre tabindex="0"><code>{chr(10).join(out)}</code></pre></div>')


def tabs(group, panels):
    """panels: list of (os_key, tab_label, inner_html). Without JavaScript every panel is shown."""
    tablist = "".join(
        f'<button class="tabs__tab" role="tab" id="{group}-tab-{k}" data-os="{k}" aria-controls="{group}-panel-{k}" '
        f'aria-selected="false" tabindex="-1">{e(lbl)}</button>' for k, lbl, _ in panels)
    body = "".join(
        f'<div class="tabs__panel" role="tabpanel" id="{group}-panel-{k}" aria-labelledby="{group}-tab-{k}">'
        f'<p class="tabs__panel-title">{e(lbl)}</p>{inner}</div>' for k, lbl, inner in panels)
    return f'<div data-tabs><div class="tabs__list" role="tablist" aria-label="Your computer">{tablist}</div>{body}</div>'


def install_tabs(group):
    return tabs(group, [
        ("mac", "macOS", term([("p", CMD_SH)], "Terminal")),
        ("linux", "Linux", term([("p", CMD_SH)], "Terminal")),
        ("windows", "Windows", term([("w", CMD_PS)], "PowerShell")),
    ])


CMD_SH_ARGS = "curl -fsSL https://shangos.madusha.dev/setup.sh | bash -s --"


def dns_tabs(group):
    def pair(kind, on, off, label):
        return term([(kind, on)], label + ": turn it on") + term([(kind, off)], label + ": turn it off again")
    return tabs(group + "-dns", [
        ("mac", "macOS", pair("p", CMD_SH_ARGS + " --dns adguard", CMD_SH_ARGS + " --dns off", "Terminal")),
        ("linux", "Linux", pair("p", CMD_SH_ARGS + " --dns adguard", CMD_SH_ARGS + " --dns off", "Terminal")),
        ("windows", "Windows", pair("w", CMD_PS_ARGS + " -Dns adguard", CMD_PS_ARGS + " -Dns off", "PowerShell")),
    ])


NAV = [("/", "Home"), ("/setup/", "Set up"), ("/guide/", "Guide"), ("/privacy/", "Privacy")]


def shell(path, title, desc, body, *, nav_key=None, og_title=None, jsonld=""):
    canon = SITE + path
    links = "".join(
        f'<a href="{h}"{" aria-current=" + chr(34) + "page" + chr(34) if h == nav_key else ""}>{t}</a>' for h, t in NAV)
    links += f'<a href="{REPO}" rel="noopener">GitHub<span class="vh"> (opens in a new tab)</span></a>'
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{e(title)}</title>
<meta name="description" content="{e(desc)}">
<link rel="canonical" href="{canon}">
<meta name="color-scheme" content="light dark">
<meta name="theme-color" content="#f9faef" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#12140e" media="(prefers-color-scheme: dark)">
<meta property="og:type" content="website">
<meta property="og:site_name" content="shangOS">
<meta property="og:title" content="{e(og_title or title)}">
<meta property="og:description" content="{e(desc)}">
<meta property="og:url" content="{canon}">
<meta property="og:image" content="{SITE}/assets/og.jpg">
<meta property="og:image:type" content="image/jpeg">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="/assets/apple-touch-icon.png">
<script>try{{var t=localStorage.getItem("shangos-theme");if(t==="light"||t==="dark")document.documentElement.dataset.theme=t}}catch(e){{}}</script>
<link rel="stylesheet" href="/assets/site.css">
<script src="/assets/site.js" defer></script>
{jsonld}</head>
<body>
<a class="skip" href="#main">Skip to content</a>
<header class="site-header">
  <div class="wrap">
    <a class="brand" href="/"><img src="/assets/logo.svg" alt="" width="32" height="32"><span>shangOS</span></a>
    <nav class="site-nav" id="site-nav" aria-label="Main">{links}</nav>
    <div class="header-tools">
      <button class="icon-btn theme-toggle" type="button" data-theme-toggle aria-pressed="false" aria-label="Switch theme">{icon("sun", "i-sun")}{icon("moon", "i-moon")}</button>
      <button class="icon-btn nav-toggle" type="button" data-nav-toggle aria-expanded="false" aria-controls="site-nav" aria-label="Open menu">{icon("menu")}</button>
    </div>
  </div>
</header>
<main id="main">
{body}
</main>
<footer class="site-footer">
  <div class="wrap">
    <div class="footer-grid">
      <div>
        <a class="brand" href="/"><img src="/assets/logo.svg" alt="" width="32" height="32"><span>shangOS</span></a>
        <p class="footer-note">A home screen for Android TV and Google TV. A hobby project, open source under the MIT licence. No accounts, no ads, no trackers, and this website sets no cookies and loads nothing from other sites.</p>
      </div>
      <div><h2>Use it</h2><ul><li><a href="/setup/">Set up on your TV</a></li><li><a href="/guide/">User guide</a></li><li><a href="{LATEST}" rel="noopener">Latest release</a></li></ul></div>
      <div><h2>Trust it</h2><ul><li><a href="/privacy/">Privacy</a></li><li><a href="/licenses/">Licences and credits</a></li><li><a href="/setup/#verify">Verify a download</a></li></ul></div>
      <div><h2>Source</h2><ul><li><a href="{REPO}" rel="noopener">GitHub</a></li><li><a href="{REPO}/issues" rel="noopener">Report a problem</a></li><li><a href="{REPO}/blob/main/CHANGELOG.md" rel="noopener">Changelog</a></li></ul></div>
    </div>
    <div class="footer-bottom"><span>&copy; shangOS contributors. MIT licence.</span><span>Not affiliated with Google, Sony or any TV maker.</span></div>
  </div>
</footer>
</body>
</html>
"""


def doc_page(path, key, title, desc, kicker, h1, lead, sections, extra_head=""):
    """sections: list of (id, heading, html). Builds the page header, the contents list and the sections."""
    toc = "".join(f'<li><a href="#{i}">{e(h)}</a></li>' for i, h, _ in sections)
    secs = "".join(f'<section id="{i}" aria-labelledby="{i}-h"><h2 id="{i}-h">{e(h)}</h2>{b}</section>' for i, h, b in sections)
    body = f"""<div class="wrap">
  <header class="page-head">
    <span class="kicker">{e(kicker)}</span>
    <h1 style="font-size:clamp(2rem,4vw + 0.5rem,3.25rem)">{h1}</h1>
    <p class="lead">{lead}</p>
    <nav class="subnav" aria-label="Documentation">
      <a href="/setup/"{' aria-current="page"' if key == "/setup/" else ""}>Set up</a>
      <a href="/guide/"{' aria-current="page"' if key == "/guide/" else ""}>Guide</a>
      <a href="/privacy/"{' aria-current="page"' if key == "/privacy/" else ""}>Privacy</a>
      <a href="/licenses/"{' aria-current="page"' if key == "/licenses/" else ""}>Licences</a>
    </nav>
  </header>
  <div class="doc">
    <div class="doc__body">{secs}</div>
    <details class="toc" open><summary>On this page</summary><nav aria-label="On this page"><h2 class="vh">On this page</h2><ol>{toc}</ol></nav></details>
  </div>
</div>"""
    return shell(path, title, desc, body, nav_key=key if key in dict(NAV) else "/guide/" if key in ("/licenses/",) else key)


def table(head, rows, stack=True, caption=""):
    th = "".join(f"<th scope=\"col\">{h}</th>" for h in head)
    trs = "".join("<tr>" + "".join(f'<td data-label="{e(head[i])}">{c}</td>' for i, c in enumerate(r)) + "</tr>" for r in rows)
    cap = f"<caption>{caption}</caption>" if caption else ""
    return f'<div class="table-wrap{" table-wrap--stack" if stack else ""}"><table>{cap}<thead><tr>{th}</tr></thead><tbody>{trs}</tbody></table></div>'


# ------------------------------------------------------------------------------------------ pages
def page_home():
    looks = [("daybreak_day", "Daybreak", "follows the sun"), ("aurora", "Aurora", "drawn in code"), ("ember", "Ember", "drawn in code"),
             ("tide", "Tide", "drawn in code"), ("orchid", "Orchid", "drawn in code"), ("glacier", "Glacier", "for bright rooms"),
             ("milkyway", "Milky Way", "photo, CC0"), ("sea", "Calm Sea", "photo, CC0")]
    look_html = "".join(
        f'<li><figure class="look"><img src="/assets/looks/{f}.webp" width="560" height="315" loading="lazy" decoding="async" '
        f'alt="The {n} wallpaper: a full-screen backdrop for the Home page"><figcaption>{n}<span>{s}</span></figcaption></figure></li>'
        for f, n, s in looks)
    faq = [
        ("Why do I need a computer?",
         "<p>Android TV and Google TV have no setting for choosing a different home app, so it is set once with <code>adb</code>, Android&rsquo;s standard developer tool, over your home network. The setup wizard does it for you and takes about five minutes.</p>"),
        ("Is it safe?",
         f'<p>You can check each part yourself. The source is public. Each release is signed, ships with a <code>SHA256SUMS</code> file, and the setup wizard checks it before installing. The app has no accounts and no analytics, and the Offline build does not even ask Android for network access. Read the <a href="/privacy/">privacy page</a> for exactly what is sent, and when.</p>'),
        ("Does it block ads in other apps?",
         '<p>shangOS itself shows none, but it does not change other apps. If you want that, the setup wizard can switch on an optional ad and tracker filter for the whole TV (Android&rsquo;s Private DNS). It is off by default, it can be undone with one command, and it has limits: <a href="/setup/#block-ads">read them first</a>.</p>'),
        ("Will it work on my TV?",
         '<p>It needs an Android TV or Google TV on Android 12 or newer, and the wizard checks that for you. It was built and measured on one small stick (an onn. Full HD Streaming Device) and has not been tested on every brand, so please <a href="' + REPO + '/issues" rel="noopener">tell me what you find</a>. Not for phones, tablets or Fire TV.</p>'),
        ("What does it change on my TV?",
         '<p>It installs one app and makes it the home screen. The TV&rsquo;s own home and setup apps stay switched on, so the TV always has a home to start with; if the TV keeps opening its own home, open shangOS from the app row. Nothing else is touched: no root, no firmware. <a href="/setup/#what-changes">Full list</a>.</p>'),
        ("How do I go back?",
         '<p>Run the wizard again and choose <em>Remove shangOS</em>. It gives Home back to your TV&rsquo;s launcher first, then removes the app. <a href="/setup/#undo">Manual steps</a> are on the setup page.</p>'),
        ("How do updates work?",
         '<p>shangOS does not check for or download updates itself. Run the wizard again, or install the new APK over the old one with <code>adb install -r</code>. Releases are signed with the same key, so your order, name and wallpaper stay.</p>'),
        ("Does it work on a phone or tablet?",
         "<p>No. It is built for the remote and a TV screen, and needs Android 12 or newer on an Android TV or Google TV device.</p>"),
        ("Who makes it?",
         f'<p>It is a hobby project, maintained in spare time and published under the MIT licence. It began as a fork of <a href="https://github.com/0xProbabillity/luma-tv" rel="noopener">Luma TV</a> (see <a href="/licenses/">credits</a>). Bugs and ideas go on <a href="{REPO}/issues" rel="noopener">GitHub</a>.</p>'),
    ]
    faq_html = "".join(f'<details><summary>{q}</summary><div class="faq__a">{a}</div></details>' for q, a in faq)
    ld = ('<script type="application/ld+json">{"@context":"https://schema.org","@type":"SoftwareApplication","name":"shangOS",'
          '"applicationCategory":"UtilitiesApplication","operatingSystem":"Android TV 12+, Google TV","license":"https://opensource.org/licenses/MIT",'
          f'"url":"{SITE}/","offers":{{"@type":"Offer","price":"0","priceCurrency":"USD"}}}}</script>\n')
    bench = bench_section()
    body = f"""
<section class="hero">
  <div class="wrap hero__grid">
    <div class="hero__copy">
      <span class="kicker">For Google TV and Android TV</span>
      <h1>A TV home screen <em>without the ads and trackers.</em></h1>
      <p class="lead">shangOS shows your apps and nothing else: no promoted rows, no sign-in, no tracking. It is free, open source and reversible, and you set it up from your computer in about five minutes.</p>
      <div class="btn-row"><a class="btn btn--primary" href="/setup/">Set it up</a><a class="btn btn--ghost" href="/privacy/">See what it sends</a></div>
      <ul class="chips" aria-label="At a glance"><li class="chip">Android 12 or newer</li><li class="chip">About 2.4 MB</li><li class="chip">MIT licence</li><li class="chip">Fully reversible</li></ul>
    </div>
    <figure class="tv">
      <div class="tv__frame"><img src="/assets/home-720.webp" srcset="/assets/home-720.webp 720w, /assets/home.webp 1280w" sizes="(min-width: 62rem) 40rem, 92vw" width="1280" height="720" fetchpriority="high" alt="The shangOS Home page on a TV: a large greeting with the weather, an Up Next row, a weather card, and a row of app tiles for Jellyfin, Proton VPN, Stremio, VLC, Settings and Pluto TV."></div>
      <div class="tv__stand" aria-hidden="true"></div>
      <figcaption>Home with the Daybreak wallpaper, from a release build.</figcaption>
    </figure>
  </div>
</section>

<section class="section" aria-labelledby="what-h">
  <div class="wrap">
    <div class="section__head"><span class="kicker">Why it exists</span><h2 id="what-h">A home screen that just shows your apps</h2><p>The first thing you see every day should not be an advert or a data collector. Everything here is reachable with the d-pad and OK: no phone, account or keyboard.</p></div>
    <div class="cards">
      <article class="card"><div class="ico">{icon("shield")}</div><h3>No ads, no promoted rows</h3><p>Home shows your own apps and what you were in the middle of watching. Nothing on it is sponsored or sold.</p></article>
      <article class="card"><div class="ico">{icon("lock")}</div><h3>Nothing leaves your TV</h3><p>No accounts, analytics or crash reports. Weather and poster pictures can use the internet, both only if you allow them, and the Offline build removes even that permission.</p></article>
      <article class="card"><div class="ico">{icon("gauge")}</div><h3>Light on cheap sticks</h3><p>Built and tested on a stick with 1.4&nbsp;GB of RAM. It picks a performance level for your TV, shows why, and lets you change it.</p></article>
      <article class="card"><div class="ico">{icon("grid")}</div><h3>Yours to arrange</h3><p>Choose the order of your apps and which widgets show: Up Next, Now Playing, Control, Inputs and Weather. Hide what you never use.</p></article>
      <article class="card"><div class="ico">{icon("image")}</div><h3>A calm look</h3><p>Eight wallpapers, a clock-over-wallpaper screen saver, and your own photo{nx("0.4.5")}. Tiles take their colour from each app&rsquo;s own icon.</p></article>
      <article class="card"><div class="ico">{icon("undo")}</div><h3>Easy to undo</h3><p>Home goes back to your TV&rsquo;s launcher before shangOS is removed. The wizard remembers what it changed so it can reverse it.</p></article>
    </div>
  </div>
</section>

<section class="section section--sunk" aria-labelledby="install-h">
  <div class="wrap">
    <div class="section__head"><span class="kicker">One line</span><h2 id="install-h">Install it from your computer</h2>
    <p>Run this in a terminal on a computer on the same Wi-Fi as the TV. A guided wizard finds the TV, shows every command before it runs, and installs the signed release.</p></div>
    <div class="quick">{install_tabs("home")}</div>
    <p class="fine" style="margin-top:var(--s-4)">First time? <a href="/setup/">The setup page</a> shows how to turn on debugging on the TV. You can <a href="/setup/#read-first">read the script before running it</a>.</p>
  </div>
</section>

<section class="section" aria-labelledby="dns-h">
  <div class="wrap split">
    <div class="stack"><span class="kicker">Optional</span><h2 id="dns-h">Block ads and trackers in your other apps too</h2>
    <p class="lead">shangOS itself shows no ads, but your other apps still can. One setting makes the whole TV refuse known ad and tracker domains.</p>
    <p>It uses Android&rsquo;s built-in <strong>Private DNS</strong>, set to AdGuard DNS or to a server of your own. It is off unless you ask for it, and one command turns it off again.</p>
    <p class="fine">Be aware: the DNS provider you pick sees the names your TV looks up. Some apps may show fewer ads, load differently, or refuse to start. Apps that use their own DNS skip the filter. <a href="/setup/#block-ads">The details and the limits</a>.</p></div>
    <div class="stack">{dns_tabs("home")}</div>
  </div>
</section>

<section class="section section--sunk" aria-labelledby="looks-h">
  <div class="wrap">
    <div class="section__head"><span class="kicker">Wallpapers</span><h2 id="looks-h">Eight looks, plus your own</h2><p>Pick one in Settings with a live preview. Daybreak follows the time of day. Milky Way and Calm Sea are photographs from Poly Haven (CC0); the other six were generated for this project.</p></div>
    <ul class="looks looks--scroll">{look_html}</ul>
  </div>
</section>

<section class="section" aria-labelledby="priv-h">
  <div class="wrap split">
    <div class="stack"><span class="kicker">Privacy</span><h2 id="priv-h">Small list of what leaves the TV</h2><p class="lead">There are two things, both under your control. Everything else stays on the device.</p><p><a class="btn btn--ghost" href="/privacy/">Read the privacy page</a></p></div>
    <div class="stack">
      <h3>Can reach the internet</h3>
      <ul class="list-clean"><li><span><strong>Weather</strong>, only if you switch it on: a coarse location of the city you chose (about 1&nbsp;km) goes to Open-Meteo.</span></li><li><span><strong>Poster pictures</strong> for Up Next: fetched from the address the streaming app publishes, on Wi-Fi or Ethernet by default.</span></li></ul>
      <h3>Never</h3>
      <ul class="list-clean list-clean--no"><li><span>Accounts, identifiers, analytics, crash reports or advertising.</span></li><li><span>Backups to the cloud. Uninstalling removes every stored byte.</span></li><li><span>Network access at all, in the Offline build.</span></li></ul>
    </div>
  </div>
</section>

{bench}

<section class="section" aria-labelledby="dl-h">
  <div class="wrap split">
    <div class="stack"><span class="kicker">Download</span><h2 id="dl-h">Prefer to do it by hand?</h2><p class="lead">Every release has two APKs (Standard and Offline) and a <code>SHA256SUMS</code> file. The setup page lists the few <code>adb</code> commands and how to check the signature.</p>
    <div class="btn-row"><a class="btn btn--primary" href="{LATEST}" rel="noopener">Latest release on GitHub</a><a class="btn btn--ghost" href="/setup/#manual">Manual steps</a></div></div>
    <div class="stack"><figure class="banner"><img src="/assets/banner.svg" alt="The shangOS banner: the word shang next to a lime play button that reads os" width="760" height="428" loading="lazy" decoding="async"><figcaption>The banner your TV shows for shangOS in its app list.</figcaption></figure>{term([("c", "# check the download, then the signer"), ("p", "sha256sum -c SHA256SUMS --ignore-missing"), ("p", "apksigner verify --print-certs shangos-*-release.apk")], "Verify")}
    <p class="fine">The certificate fingerprint must match the release notes and the TV&rsquo;s own Settings, Privacy, This build screen.</p></div>
  </div>
</section>

<section class="section section--sunk" aria-labelledby="faq-h">
  <div class="wrap" style="max-width:52rem">
    <div class="section__head"><span class="kicker">Questions</span><h2 id="faq-h">Good to know first</h2></div>
    <div class="faq">{faq_html}</div>
  </div>
</section>
"""
    return shell("/", "shangOS: a calmer home screen for Android TV and Google TV",
                 "shangOS is an open-source home screen for Android TV and Google TV. Three quiet pages, widgets you choose, no accounts, no ads, no tracking. Reversible, and set up from your computer in about five minutes.",
                 body, nav_key="/", og_title="shangOS: a calmer home screen for your TV", jsonld=ld)


def brand_table():
    return table(["Your device", "Turn on Developer options", "Then open"], [
        ["Google TV Streamer, Chromecast with Google TV, onn., TCL, Hisense, Sony BRAVIA (Google TV)", "Settings, System, About, press <strong>Android TV OS build</strong> 7 times", "Settings, System, Developer options"],
        ["Sony BRAVIA (older Android TV), Philips, Sharp", "Settings, Device Preferences, About, press <strong>Build</strong> 7 times", "Settings, Device Preferences, Developer options"],
        ["Xiaomi Mi Box S, Mi TV Stick, Xiaomi TV", "Settings, Device Preferences, About, press <strong>Build</strong> 7 times", "Settings, Device Preferences, Developer options"],
        ["NVIDIA Shield TV", "Settings, Device Preferences, About, press <strong>Build</strong> 7 times", "Settings, Device Preferences, Developer options"],
    ], caption="Menu names change between firmware versions. If yours differ, search Settings for &ldquo;Build&rdquo; and &ldquo;Developer options&rdquo;.")


def page_setup():
    s = []
    s.append(("before", "Before you start", f"""
<p>You need an Android TV or Google TV on <strong>Android 12 or newer</strong>, a computer on the same Wi-Fi or network cable, and the TV remote. It takes about five minutes. It needs no root and replaces no firmware.</p>
<div class="callout" id="what-changes"><h3>What changes on your TV</h3><p>One app is installed, and it is set as the home screen. The TV&rsquo;s own home and setup apps are left switched on (on some Google TVs the TV cannot start without them). If you choose them, three optional permissions are granted (Up Next, Now Playing, recent apps) and the shangOS screen saver is selected. Nothing else is touched. HDMI, Chromecast, picture settings and Bluetooth remotes keep working.</p></div>
<p class="muted">Not for phones and tablets. Not tested on Fire TV.</p>"""))
    s.append(("debugging", "1. Turn on debugging on the TV", f"""
<ol class="steps">
<li><div><h3>Become a developer</h3><p>Find the build entry for your TV in the table below, and press OK on it seven times until a message says <em>You are now a developer</em>.</p>{brand_table()}</div></li>
<li><div><h3>Switch debugging on</h3><p>In Developer options, turn on <strong>USB debugging</strong>. Some TVs call it <strong>Network debugging</strong>. On Android 11 and newer you may see <strong>Wireless debugging</strong> instead; see <a href="#pairing">pairing</a> below.</p></div></li>
<li><div><h3>Allow your computer</h3><p>The first time the computer connects, the TV asks <em>Allow USB debugging?</em>. Choose <strong>Always allow from this computer</strong> and press OK. Look at the TV screen, not the computer.</p></div></li>
<li><div><h3>Find the TV&rsquo;s address (optional)</h3><p>The wizard usually finds the TV by itself. If it cannot, note the IP address under Settings, Network &amp; Internet, then your connection (for example <code>192.168.1.50</code>).</p></div></li>
</ol>"""))
    s.append(("wizard", "2. Run the setup wizard", f"""
<p>Open a terminal (macOS, Linux) or PowerShell (Windows), and run the line for your computer. No administrator rights are needed.</p>
{install_tabs("setup")}
<p>The wizard is five short steps. Move with the arrow keys (or <kbd>j</kbd>/<kbd>k</kbd>), choose with <kbd>Enter</kbd>, switch options with <kbd>Space</kbd>, go back with <kbd>Esc</kbd>, quit with <kbd>q</kbd>.</p>
<ol class="steps">
<li><div><h3>TV</h3><p>Lists TVs adb already knows, TVs announcing Wireless debugging on your network, and addresses you saved. Or type an address, or pair with a code. It checks the TV runs Android 12 or newer.</p></div></li>
<li><div><h3>Build</h3><p><strong>Standard</strong>, or <strong>Offline</strong> which has no internet permission at all (no weather, no remote posters).</p></div></li>
<li><div><h3>Options</h3><p>Optional permissions, making it the home screen, a faster compile, the screen saver, and <a href="#block-ads">ad and tracker blocking</a> (off unless you tick it).</p></div></li>
<li><div><h3>Review</h3><p>Shows the exact commands, then asks once.</p></div></li>
<li><div><h3>Install</h3><p>Downloads the release from GitHub, checks its SHA-256 against the published <code>SHA256SUMS</code>, installs it, and applies your options. Every command that changes the TV is printed first, on a line starting with <code>+</code>.</p></div></li>
</ol>
<h3>Without a menu</h3>
<p>For scripts and unattended installs, pass options. <code>--yes</code> (<code>-Yes</code> on Windows) takes every default and asks nothing.</p>
{tabs("flags", [
    ("mac", "macOS", term([("p", "curl -fsSL https://shangos.madusha.dev/setup.sh | bash -s -- --tv 192.168.1.50:5555 --variant offline --yes")], "Terminal")),
    ("linux", "Linux", term([("p", "curl -fsSL https://shangos.madusha.dev/setup.sh | bash -s -- --tv 192.168.1.50:5555 --variant offline --yes")], "Terminal")),
    ("windows", "Windows", term([("w", CMD_PS_ARGS + " -Tv 192.168.1.50:5555 -Variant offline -Yes")], "PowerShell")),
])}
{table(["Option", "Windows", "What it does"], [
    ["<code>--tv ADDRESS</code>", "<code>-Tv</code>", "The TV&rsquo;s address, with or without <code>:5555</code>."],
    ["<code>--variant release|offline</code>", "<code>-Variant</code>", "Standard (default) or Offline."],
    ["<code>--release v0.4.0</code>", "<code>-Release</code>", "A specific release instead of the latest."],
    ["<code>--apk FILE</code>", "<code>-Apk</code>", "Install an APK you already have."],
    ["<code>--no-home</code>, <code>--no-widgets</code>, <code>--no-aot</code>", "<code>-NoHome</code>, <code>-NoWidgets</code>, <code>-NoAot</code>", "Skip one step."],
    ["<code>--saver</code>", "<code>-Saver</code>", "Also use the shangOS screen saver."],
    ["<code>--block-ads</code>", "<code>-BlockAds</code>", "Also turn on ad and tracker blocking for the whole TV (<a href=\"#block-ads\">read the limits</a>)."],
    ["<code>--dns adguard|HOSTNAME|off</code>", "<code>-Dns</code>", "Only change the TV&rsquo;s Private DNS; no install."],
    ["<code>--status</code>", "<code>-Status</code>", "Show what is installed and what Home opens."],
    ["<code>--uninstall</code>", "<code>-Uninstall</code>", "Undo everything."],
    ["<code>--pair HOST:PORT --code 123456</code>", "<code>-Pair</code>, <code>-Code</code>", "Pair with a TV that shows a code."],
    ["<code>--plain</code>, <code>--no-color</code>", "<code>-Plain</code>, <code>-NoColor</code>", "No menus, or no colours."],
])}
<div class="callout" id="read-first"><h3>Read it before you run it</h3><p>It is a plain text script. The wizard only talks to GitHub (to download the release) and to your TV; the one exception is your own package manager, if you let it install adb.</p></div>
{tabs("read", [
    ("mac", "macOS", term([("p", "curl -fsSL -o setup.sh https://shangos.madusha.dev/setup.sh"), ("p", "less setup.sh"), ("p", "bash setup.sh")], "Terminal")),
    ("linux", "Linux", term([("p", "curl -fsSL -o setup.sh https://shangos.madusha.dev/setup.sh"), ("p", "less setup.sh"), ("p", "bash setup.sh")], "Terminal")),
    ("windows", "Windows", term([("w", PS_FETCH + " | Set-Content setup.ps1"), ("w", "notepad setup.ps1"), ("w", "powershell -ExecutionPolicy Bypass -File .\\setup.ps1")], "PowerShell")),
])}
<p><strong>No adb on your computer?</strong> The wizard offers to install it with your package manager (Homebrew, apt, dnf, pacman, zypper, or on Windows winget, Scoop or Chocolatey) and shows the command first. Or get <a href="https://developer.android.com/tools/releases/platform-tools" rel="noopener">platform-tools</a> from Google.</p>"""))
    s.append(("block-ads", "Optional: block ads and trackers on the whole TV", f"""
<p>shangOS itself shows no ads, but your other apps can. This optional step sets the TV&rsquo;s <strong>Private DNS</strong> (an Android setting since Android 9) to a server that refuses known ad and tracker domains. It applies to every app that uses the TV&rsquo;s normal DNS, and it does not touch shangOS.</p>
{dns_tabs("setup")}
<p>Or tick <em>Block ads and trackers</em> in the wizard&rsquo;s options, or pass <code>--block-ads</code> (<code>-BlockAds</code>) when installing. Choose <em>Use my own DNS hostname</em> in the wizard&rsquo;s menu for a NextDNS profile, your own AdGuard Home or a Pi-hole that speaks DNS-over-TLS. The wizard checks that the TV can still look up names afterwards, and puts the old setting back if it cannot.</p>
<h3>By hand</h3>
{term([("p", "adb shell settings put global private_dns_specifier dns.adguard-dns.com"), ("p", "adb shell settings put global private_dns_mode hostname")], "Turn it on")}
{term([("p", "adb shell settings put global private_dns_mode opportunistic")], "Turn it off again (back to the TV's default, called Automatic)")}
<h3>What to know first</h3>
{table(["Topic", "Details"], [
    ["Who sees what", "The DNS provider sees the names your TV looks up (for example <code>example.com</code>), though not the pages. Pick one you trust and read its privacy policy. The default here is AdGuard DNS (<code>dns.adguard-dns.com</code>), which blocks ads, trackers and known malicious domains."],
    ["It is system-wide", "Free streaming apps may show fewer ads, load slowly, or refuse to start when their ad servers do not answer. If that happens, turn it off."],
    ["Apps can skip it", "Apps that bring their own DNS, or a fixed one, ignore the TV&rsquo;s setting. Nothing on the TV can stop that without root."],
    ["Needs", "Android 9 or newer. The wizard checks that names still resolve after the change and undoes it if not; if the TV was offline when you set it, it cannot check."],
    ["Undo", "<code>--dns off</code> (<code>-Dns off</code>) puts back whatever the TV had before, or Automatic. Removing shangOS leaves it as it is, and offers to undo it."],
], stack=True)}
<div class="callout"><h3>For apps that skip the filter (advanced)</h3><p>The place to catch them is your router, not the TV. Run a filter on your own network (Pi-hole or AdGuard Home), send all DNS requests on port 53 to it, and block outgoing port 853 (DNS-over-TLS) so apps cannot use another server. DNS-over-HTTPS looks like ordinary web traffic, so it can only be stopped with a domain list of known servers. <strong>Do one or the other:</strong> blocking port 853 also blocks the Private DNS setting above, unless its server is on your own network.</p></div>"""))
    s.append(("pairing", "TVs that show a pairing code", f"""
<p>Newer Google TV builds can use <strong>Wireless debugging</strong>, which asks for a one-time pairing first.</p>
<ol class="steps">
<li><div><h3>Open the pairing box</h3><p>Developer options, Wireless debugging, <strong>Pair device with pairing code</strong>. The TV shows an address with a port, and a six-digit code. Keep that box open.</p></div></li>
<li><div><h3>Pair</h3><p>In the wizard choose <em>Pair with a code</em>, or run it yourself:</p>{tabs("pair", [
    ("mac", "macOS", term([("p", "adb pair 192.168.1.50:37123")], "Terminal")),
    ("linux", "Linux", term([("p", "adb pair 192.168.1.50:37123")], "Terminal")),
    ("windows", "Windows", term([("w", "adb pair 192.168.1.50:37123")], "PowerShell"))])}<p>Type the six-digit code when asked.</p></div></li>
<li><div><h3>Connect</h3><p>Close the box. The main Wireless debugging screen shows a <em>different</em> port. Use that one: <code>adb connect 192.168.1.50:41234</code>, or pick the TV in the wizard (it appears under &ldquo;Found on your network&rdquo;).</p></div></li>
</ol>"""))
    s.append(("manual", "Do it by hand with adb", f"""
<p>The wizard runs exactly these. Replace the address and the file name with yours.</p>
{term([("c", "# 1. connect, and accept the prompt on the TV"), ("p", "adb connect 192.168.1.50:5555"), ("c", "# 2. install the signed release"), ("p", "adb install -r shangos-v0.4.0-release.apk"), ("c", "# 3. make it the home screen"), ("p", "adb shell cmd package set-home-activity --user 0 dev.madusha.shangos/.MainActivity")], "adb")}
<p>Press Home on the remote. If the TV still opens its own launcher, that is normal on some Google TVs. Open shangOS from the app row, or choose &ldquo;Always&rdquo; if the TV asks which home to use. Do not switch the TV&rsquo;s own home or setup app off unless you have read <a href="#loop">the warning below</a>: on some Google TVs the TV then cannot start.</p>
<p>Optional permissions for Up Next, Now Playing and recent apps:</p>
{term([("p", "adb shell pm grant dev.madusha.shangos android.permission.READ_TV_LISTINGS"), ("p", "adb shell cmd notification allow_listener dev.madusha.shangos/dev.madusha.shangos.MediaListener"), ("p", "adb shell appops set dev.madusha.shangos GET_USAGE_STATS allow")], "adb")}"""))
    s.append(("undo", "Undo everything", f"""
<p>Run the wizard and choose <em>Remove shangOS</em>, or pass the uninstall option:</p>
{tabs("undo", [
    ("mac", "macOS", term([("p", "curl -fsSL https://shangos.madusha.dev/setup.sh | bash -s -- --uninstall")], "Terminal")),
    ("linux", "Linux", term([("p", "curl -fsSL https://shangos.madusha.dev/setup.sh | bash -s -- --uninstall")], "Terminal")),
    ("windows", "Windows", term([("w", CMD_PS_ARGS + " -Uninstall")], "PowerShell"))])}
<p>By hand, switch your TV&rsquo;s launcher back on <em>before</em> removing shangOS, so the TV always has a home screen:</p>
{term([("p", "adb shell pm enable --user 0 com.google.android.tvlauncher"), ("c", "# or com.google.android.apps.tv.launcherx on newer Google TV builds"), ("p", "adb uninstall dev.madusha.shangos")], "adb")}
<div class="callout callout--warn"><h3>Order matters</h3><p>Removing shangOS while the TV&rsquo;s own launcher is still switched off can leave the TV with no home screen. If that happens, run <code>adb shell pm enable --user 0 com.google.android.tvlauncher</code> from your computer.</p></div>"""))
    s.append(("loop", "If your TV loops after a restart", f"""
<div class="callout callout--warn"><h3>Some Google TVs cannot start without their own home or setup app</h3><p>Older versions of this wizard switched those off to make shangOS the home screen. On some Google TVs (a Sony Bravia, for one) that leaves the TV unable to start: a blank screen, a restart, and round again. Since 0.6 the wizard never switches the setup app off, leaves the TV&rsquo;s own home on unless you pass <code>--disable-stock</code>, and switches anything an older version turned off back on.</p></div>
<p><strong>If your TV is looping now:</strong></p>
<ol class="steps">
<li><div><h3>Switch the TV off at the wall</h3><p>Leave it off for 10 to 15 minutes, then switch it on again. Have your computer ready.</p></div></li>
<li><div><h3>Switch them back on as soon as the TV answers</h3><p>Each loop gives a short window. Keep trying until the commands go through:</p>
{term([("p", "adb connect 192.168.1.50:5555"), ("p", "adb shell pm enable com.google.android.apps.tv.launcherx"), ("p", "adb shell pm enable com.google.android.tungsten.setupwraith"), ("p", "adb shell pm enable com.google.android.tvlauncher")], "adb")}
<p>Some of these are not on your TV; the error for those is harmless.</p></div></li>
<li><div><h3>Then run the wizard again</h3><p>The current version leaves them on. If you set shangOS up with an older version and the TV is not looping, run the wizard once before the next restart: it switches them back on by itself.</p></div></li>
</ol>
<p>If shangOS itself crashes twice within five minutes, it stands aside and lets the TV&rsquo;s own home open, and tries again a few minutes later, so a broken build cannot hold the TV in a loop either.</p>"""))
    s.append(("verify", "Verify a download", f"""
<p>If you download the APK yourself, check it before installing. Put the APK and <code>SHA256SUMS</code> from the same release in one folder.</p>
{tabs("verify", [
    ("mac", "macOS", term([("p", "shasum -a 256 -c SHA256SUMS --ignore-missing"), ("p", "apksigner verify --print-certs shangos-v0.4.0-release.apk")], "Terminal")),
    ("linux", "Linux", term([("p", "sha256sum -c SHA256SUMS --ignore-missing"), ("p", "apksigner verify --print-certs shangos-v0.4.0-release.apk")], "Terminal")),
    ("windows", "Windows", term([("w", "(Get-FileHash .\\shangos-v0.4.0-release.apk -Algorithm SHA256).Hash"), ("c", "# compare with the line in SHA256SUMS"), ("w", "apksigner verify --print-certs shangos-v0.4.0-release.apk")], "PowerShell"))])}
<p><code>apksigner</code> is part of Android&rsquo;s build-tools. The certificate SHA-256 it prints must match the release notes on GitHub, and the one shown on the TV under Settings, Privacy, This build.</p>"""))
    s.append(("troubleshooting", "If something goes wrong", table(["What you see", "What to do"], [
        ["<code>failed to connect</code>, or the TV is not listed", "The TV and computer must be on the same network, and debugging must be on. Wake the TV first. Try the address with <code>:5555</code>. On newer builds use Wireless debugging and <a href=\"#pairing\">pair</a>."],
        ["<code>unauthorized</code>", "Look at the TV. An <em>Allow USB debugging?</em> box is waiting: choose Always allow from this computer, then OK."],
        ["Home still opens the old launcher", "Some TVs insist on their own. shangOS leaves it on, because on some Google TVs the TV cannot start without it. Open shangOS from the app row. At your own risk, <code>--disable-stock</code> (<code>-DisableStock</code> on Windows) switches the launcher off after asking; read <a href=\"#loop\">the warning</a> first."],
        ["<code>INSTALL_FAILED_UPDATE_INCOMPATIBLE</code>", "The TV has shangOS signed with another key, for example a build you made yourself. Android will not update it in place. The wizard offers to remove the old one first; that clears shangOS&rsquo;s own settings only."],
        ["<code>INSTALL_FAILED_VERSION_DOWNGRADE</code>", "The TV has a newer version than the one you are installing. The wizard offers to downgrade."],
        ["No TV found by the wizard", "Some routers block the announcements that discovery uses. Give the address yourself: <code>--tv 192.168.1.50:5555</code>."],
        ["GitHub is rate-limiting", "Wait a few minutes, or download the APK from the <a href=\"" + LATEST + "\" rel=\"noopener\">release page</a> and pass it with <code>--apk</code>."],
        ["Windows: &ldquo;running scripts is disabled&rdquo;", "That applies to saved files only. The one-line command is not affected. For a saved copy, use <code>powershell -ExecutionPolicy Bypass -File .\\setup.ps1</code>."],
        ["Apps stopped loading after I turned on ad blocking", "Turn it off with <code>--dns off</code> (<code>-Dns off</code>). Some apps depend on the ad servers answering."],
        ["Colours or boxes look broken", "Use a modern terminal (Terminal, iTerm2, GNOME Terminal, Windows Terminal), or add <code>--plain</code> (<code>-Plain</code>) for plain text."],
    ])))
    return doc_page("/setup/", "/setup/", "Set up shangOS on your TV | shangOS",
                    "Put shangOS on Google TV or Android TV from a Mac, Linux or Windows computer: turn on debugging, run one command, follow the wizard. Reversible.",
                    "Set up", "Put shangOS on your TV",
                    "Turn on debugging, run one command on your computer, follow the wizard. About five minutes, and you can undo all of it.", s)


def page_guide():
    nx_042, nx_043, nx_045 = nx("0.4.2"), nx("0.4.3"), nx("0.4.5")
    nx_046, nx_050, nx_060 = nx("0.4.6"), nx("0.5.0"), nx("0.6.0")
    s = []
    s.append(("home", "The Home page", f"""
<p>Home opens with a greeting (set your name in Settings), the date, and your wallpaper. The row of app tiles sits below. The focused tile glows, the wallpaper tilts slightly as you move, and pressing OK opens the app out of its tile. The light behind the row takes the colour of the focused app (Settings, Room lighting turns it off).{nx_043}</p>
<p>The line under each app&rsquo;s name says when you last opened it. With Continue watching allowed in Privacy, it can say what you were in the middle of instead (&ldquo;Continue: &hellip; &middot; 18 min left&rdquo;), with the programme&rsquo;s artwork faded in behind the greeting.{nx_043}{nx_050} When you open an app and come back, focus is on that app&rsquo;s tile again.{nx_042}</p>
<p>The page switcher at the top moves between Home, Apps and Settings. The clock can be hidden. A small lock appears beside it while a VPN is on, and an amber crossed Wi-Fi when the TV is offline.{nx_050}</p>
<p>Bars, cards and menus are frosted glass: they show a blurred copy of your wallpaper behind them (Settings, Frosted glass).{nx_050} After 20 quiet seconds the tab bar and clock fade back so the wallpaper has the room, and any key brings them back (Settings, Calm mode).{nx_050}</p>"""))
    s.append(("apps", "The Apps page", f"""
<p>Every installed app, including the ones the TV hides from its own list. The order is shared with the Home row. Hold OK on a tile to open its menu:</p>
<ul><li><strong>Open</strong> and <strong>Move to first position</strong>.</li><li><strong>Hide from Home</strong> keeps the app in Apps and takes it off the Home row.</li><li><strong>App info</strong> opens Android&rsquo;s page for the app.</li><li><strong>Uninstall</strong> asks Android to remove an app you installed; Android asks you to confirm. It is not offered for the TV&rsquo;s own apps.{nx_045}</li></ul>
<p><strong>Grab to move.</strong> Hold OK on a tile and it lifts. Move it with the d-pad and press OK to put it down; Back puts it down where it is. Put down without moving it, and the menu opens.{nx_045} Before 0.4.5 the menu has Move left and Move right instead.</p>"""))
    s.append(("widgets", "Widgets", """
<p>Widgets sit below the app row on Home. Press up and down to cycle them. Settings, Home widgets opens an editor where you can reorder and switch them off, with a live preview.</p>""" + table(["Widget", "Shows", "Needs"], [
        ["Up Next", "Items from your streaming apps&rsquo; Continue watching rows, and recently used apps. OK jumps into the programme.", "Optional: permission to read TV listings, and usage access for recent apps."],
        ["Now Playing", "What is playing in Spotify, YouTube Music, Plex and similar apps: cover, progress and remote keys.", "Optional: notification access. Android needs it to see media sessions; no notification is read."],
        ["Control", "Tiles for Wi-Fi, picture mode, sound and Bluetooth remotes. Each opens the matching system screen.", "Nothing."],
        ["Inputs", "Your HDMI inputs and tuner, with the names your connected devices report.", "Nothing."],
        ["Weather", "Hourly and seven-day forecast for your chosen city, or the main city of your TV&rsquo;s time zone. Cached on the TV.", "Off until you turn it on. Uses the internet; see <a href=\"/privacy/\">Privacy</a>."],
    ])))
    s.append(("settings", "Settings", """
<p>Settings opens over a soft scrim so you see wallpaper changes as you make them. The first page lists five entries: Appearance, Motion and sound, Home, Privacy and System. Each group is a page of its own, and Back returns to the first page.{nx_060}</p>""" + table(["Section", "What it does"], [
        ["Appearance: Wallpaper", "A gallery of all eight looks, with a full-screen preview, and your own photo once you add one (from 0.4.5)."],
        ["Appearance: Your name", "The greeting on Home. Stored only on the TV."],
        ["Home: City", "Where the weather and the sunrise are for. Choose your city from a list built into the app (also in the first-run setup), or keep Automatic, the main city of your TV&rsquo;s time zone. Nothing is looked up and nothing leaves the TV (from 0.4.6)."],
        ["Home: Temperature", "Automatic follows the country of the city, or choose Celsius or Fahrenheit."],
        ["Appearance: Clock", "Show or hide the clock."],
        ["Motion and sound: Performance", "Auto, Light, Balanced or Quality. See below."],
        ["Home: Home widgets", "The live widget editor."],
        ["Home: Apps order", "How the Apps tab is ordered: your own order, A to Z, or recently used (from 0.6.0)."],
        ["Home: Hidden apps", "The apps hidden from Home; choose one to show it again (from 0.6.0)."],
        ["Motion and sound: Room lighting", "The coloured light behind the app row (from 0.4.3)."],
        ["Appearance: Frosted glass", "Bars, cards and menus show a blurred copy of the wallpaper behind them. Off with the Light performance level (from 0.5.0)."],
        ["Motion and sound: Calm mode", "The tab bar and clock fade back after 20 quiet seconds (from 0.5.0)."],
        ["Privacy", "Switches for weather and posters, a runtime Offline mode, and the signing fingerprint of this build."],
        ["System: Setup guide", "Shows the first-run walkthrough again, with a QR code for the setup page."],
        ["System: About", "Version, credits and licences."],
    ])))
    s.append(("wallpapers", "Wallpapers and your own photo", """
<p>Eight looks are included. Aurora, Ember, Tide, Orchid, Glacier (clear for bright rooms) and Daybreak (changes through the day) were generated in code for this project. Milky Way and Calm Sea are CC0 photographs from Poly Haven.</p>
<p><strong>Your own photo</strong> (from 0.4.5)<strong>:</strong> from a computer on the same network, with a copy of the repository, run <code>./shangos.sh wallpaper ~/photo.jpg</code>. The picture goes straight into shangOS&rsquo;s private storage over adb: no media permission, no Photos or cloud account, no network. It appears in Settings, Wallpaper as &ldquo;Your photo&rdquo;, and the screen saver shows it dimmed. <code>./shangos.sh wallpaper --remove</code> deletes it.</p>
<p>The screen saver (optional, in the setup wizard) shows a clock over your wallpaper after your TV&rsquo;s own screen-saver delay.</p>"""))
    s.append(("performance", "Performance levels", """
<p>On first run shangOS looks at the TV&rsquo;s memory, processor and core count, and picks a level. Settings shows why, and you can change it.</p>""" + table(["Level", "What it means"], [
        ["Auto", "The level shangOS picked for this TV. The default."],
        ["Light", "Static wallpapers and no background drawing. For sticks with about 1 to 1.5 GB of RAM."],
        ["Balanced", "Gentle wallpaper motion and parallax."],
        ["Quality", "All lighting and motion effects, for TVs with plenty of headroom."],
    ])))
    s.append(("privacy", "Privacy controls", """
<p>One Settings screen shows the live state of everything that can touch the network.</p>
<ul><li><strong>Weather</strong> is off until you turn it on, and tells you what it will send before the first request.</li><li><strong>Remote posters</strong>: Off, Wi-Fi and Ethernet only, or Always.</li><li><strong>Offline mode</strong> stops the app&rsquo;s own network use at once, without reinstalling.</li><li><strong>This build</strong> shows the SHA-256 fingerprint of the installed APK to compare with the release notes.</li></ul>
<p>For the full list see <a href="/privacy/">Privacy</a>.</p>"""))
    s.append(("updates", "Updates and removal", """
<p>shangOS does not update itself. New releases are signed with the same key, so you install them over the old one (run the setup wizard again, or <code>adb install -r</code>) and keep your order, name and wallpaper. To go back to your TV&rsquo;s launcher, see <a href="/setup/#undo">Undo</a>.</p>"""))
    s.append(("benchmarks", "Measure your own TV", f"""
<p>The numbers on the home page come from one TV. To measure yours, install shangOS (and, if you like, Luma TV) on it and run the benchmark from a copy of the <a href="{REPO}" rel="noopener">repository</a>. It runs each launcher in turn, with the same steps and the same wallpaper, and gives the TV&rsquo;s original Home back at the end.</p>
{term([("p", "tools/bench.sh -s 192.168.1.50:5555"), ("c", "# writes docs/data/bench.json: which TV, which versions, when, and every number")], "Repository folder")}
<p>The file is checked by <code>python3 tools/bench-json.py --check docs/data/bench.json</code>, and the release build publishes it next to the APKs. The method and what each number means are in <a href="{REPO}/blob/main/docs/MEASURING.md" rel="noopener">MEASURING.md</a>.</p>"""))
    s.append(("developers", "For developers", f"""
<p>shangOS is plain Java with no third-party libraries, one process and one activity. The architecture notes, the release process and the build and test scripts are in the repository.</p>
<ul><li><a href="{REPO}/blob/main/docs/dev/ARCHITECTURE.md" rel="noopener">Architecture</a></li><li><a href="{REPO}/blob/main/docs/dev/RELEASING.md" rel="noopener">Releasing</a></li><li><a href="{REPO}/blob/main/docs/TESTING.md" rel="noopener">Building and testing</a></li></ul>"""))
    return doc_page("/guide/", "/guide/", "User guide | shangOS",
                    "How to use shangOS with a TV remote: Home, Apps, widgets, Settings, wallpapers, performance levels and privacy controls.",
                    "User guide", "Using shangOS",
                    "Everything is reachable with the d-pad and OK. This is a tour of what is where.", s)


def page_privacy():
    s = []
    s.append(("network", "What can use the network", """
<p>Two features in the Standard build can open a network connection. Both are under your control. The Offline build has neither.</p>""" + table(["Feature", "When", "What is sent, and to whom"], [
        ["Weather", "Only if you turn it on. Off by default.", "An HTTPS request to <code>api.open-meteo.com</code> with a coarse location (two decimals, about 1&nbsp;km) of the city you chose in setup or Settings (picked from a list built into the app, so choosing it sends nothing), or else the main city of your TV&rsquo;s time zone. It is never taken from GPS or looked up from your internet address. Open-Meteo sees the request and your internet address, as any website would."],
        ["Poster pictures", "Wi-Fi and Ethernet only, by default. You can set it to Off or Always.", "Streaming apps publish a poster address for their Continue watching items. shangOS fetches that picture from the address the app gave. It does not fetch from addresses on your home network."],
    ]) + """
<div class="callout"><h3>Offline, two ways</h3><p><strong>Offline mode</strong> in Settings, Privacy stops the app&rsquo;s own network use immediately. The <strong>Offline build</strong> is compiled without the <code>INTERNET</code> permission, so Android itself refuses it any network access.</p></div>"""))
    s.append(("permissions", "Permissions it asks for", table(["Permission", "Why", "When"], [
        ["<code>READ_TV_LISTINGS</code>", "Lets Up Next show Continue watching items that streaming apps share. Read only.", "Optional"],
        ["<code>READ_EPG_DATA</code>", "Goes with the permission above: the system&rsquo;s TV provider asks for both before it shares Continue watching items.", "Optional"],
        ["<code>PACKAGE_USAGE_STATS</code>", "Orders recent apps by when you last opened them.", "Optional"],
        ["<code>BIND_NOTIFICATION_LISTENER</code>", "Lets Now Playing see media sessions. It reads media playback only, not your notifications.", "Optional"],
        ["<code>REQUEST_DELETE_PACKAGES</code>", "Lets shangOS ask Android to show its uninstall confirmation. Nothing is removed unless you confirm there.", "When you choose Uninstall" + nx("0.4.5")],
        ["<code>ACCESS_NETWORK_STATE</code>", "Tells whether you are on Wi-Fi or Ethernet before fetching posters.", "System"],
        ["<code>INTERNET</code>", "Weather and poster pictures. Not present in the Offline build.", "Standard build only"],
    ])))
    s.append(("dns", "Ad blocking is separate", f"""
<p>The optional ad and tracker filter is a setting of your TV, not a feature of the app. shangOS does not look up or send any names itself. If you switch it on, the DNS provider you chose sees the domain names your TV looks up, for every app. See <a href="/setup/#block-ads">the setup page</a> for what to weigh first.</p>"""))
    s.append(("storage", "What is stored", """
<p>Your settings, app order, name and cached pictures live in the app&rsquo;s private storage on the TV. Cloud backup is switched off in the app&rsquo;s manifest (<code>allowBackup=false</code>). Uninstalling removes all of it.</p>
<p>There are no accounts, no analytics, no crash reporting and no identifiers. This website sets no cookies and loads nothing from other sites; the only thing it remembers, in your browser, is your theme choice.</p>"""))
    s.append(("audit", "Check it yourself", f"""
<p>All network code is in one file, <code>Net.java</code>, and the repository&rsquo;s audit script fails if sockets or web views appear anywhere else. Settings holds all preferences in <code>Prefs.java</code>.</p>
<p>To confirm you are running the build you think you are, open Settings, Privacy, This build on the TV and compare the SHA-256 fingerprint with the one in the release notes on <a href="{LATEST}" rel="noopener">GitHub</a>. This page mirrors the Privacy screen in the app.</p>"""))
    return doc_page("/privacy/", "/privacy/", "Privacy | shangOS",
                    "Exactly what shangOS sends over the network and when, which permissions it asks for, and what it stores. No accounts, analytics or trackers.",
                    "Privacy", "What shangOS sends, and when",
                    "No accounts, no analytics, no crash reports, no identifiers. Two optional features can use the internet, and the Offline build has neither.", s)


def page_licenses():
    s = []
    s.append(("components", "What is in it", table(["Component", "From", "Licence", "Used for"], [
        ["shangOS", "madushas and contributors", "MIT", "The launcher itself."],
        ["Luma TV", "0xProbabillity", "MIT", "The project shangOS began as a fork of. Its licence notice is kept."],
        ["Poly Haven photographs", "Poly Haven and its artists", "CC0 1.0", "The Milky Way and Calm Sea wallpapers."],
        ["Open-Meteo", "Open-Meteo.com", "CC BY 4.0", "Weather forecasts, only if you turn Weather on."],
        ["IANA time zone database", "IANA", "Public domain", "Linking a time zone to a city for weather and sunrise when no city is chosen."],
        ["GeoNames", "GeoNames contributors, via the all-the-cities package (MIT)", "CC BY 4.0", "The built-in list of about 23,000 cities for Settings, City. Nothing is looked up online."],
        ["Roboto", "Google", "Apache 2.0", "Letters in the TV banner image."],
    ]) + """
<p>shangOS contains no third-party Java libraries. The full texts are bundled in the app (Settings, About, Licences) and kept as <code>LICENSE</code> and <code>NOTICE.md</code> in the repository.</p>"""))
    s.append(("wallpapers", "Wallpapers", """
<p>Aurora, Ember, Tide, Orchid, Glacier and Daybreak were generated in code for this project and are published under the MIT licence. Milky Way and Calm Sea are photographs from <a href="https://polyhaven.com" rel="noopener">Poly Haven</a>, released as CC0.</p>"""))
    s.append(("trademarks", "Names", """
<p>Product and app names shown on this site (for example in screenshots) belong to their owners. shangOS is not affiliated with or endorsed by Google, Sony, or any TV or streaming company.</p>"""))
    return doc_page("/licenses/", "/licenses/", "Licences and credits | shangOS",
                    "The licences and credits for shangOS: its MIT licence, the Luma TV project it began from, and the wallpapers and data it uses.",
                    "Credits", "Licences and credits",
                    "What is bundled or used, who made it, and under which licence. This mirrors Settings, About, Licences in the app.", s)


def page_404():
    body = """
<div class="wrap lost"><div>
  <img class="lost__logo" src="/assets/logo.svg" alt="" width="72" height="72">
  <p class="code" aria-hidden="true">404</p>
  <h1 style="font-size:clamp(1.75rem,3vw + 0.5rem,2.5rem)">That page is not here</h1>
  <p class="lead">The link may be old or mistyped. These are the places most people are looking for.</p>
  <div class="btn-row" style="justify-content:center"><a class="btn btn--primary" href="/setup/">Set up your TV</a><a class="btn btn--ghost" href="/">Home</a><a class="btn btn--ghost" href="/guide/">User guide</a></div>
</div></div>"""
    return shell("/404.html", "Page not found | shangOS", "That page is not here. Try the setup guide or the home page.", body)


def outputs():
    pages = {"index.html": page_home(), "setup/index.html": page_setup(), "guide/index.html": page_guide(),
             "privacy/index.html": page_privacy(), "licenses/index.html": page_licenses(), "404.html": page_404()}
    pages["sitemap.xml"] = ('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
                            + "".join(f"  <url><loc>{SITE}{u}</loc></url>\n" for u in ["/", "/setup/", "/guide/", "/privacy/", "/licenses/"])
                            + "</urlset>\n")
    pages["robots.txt"] = f"User-agent: *\nAllow: /\nSitemap: {SITE}/sitemap.xml\n"
    return pages


def main():
    pages = outputs()
    if "--check" in sys.argv:  # CI: the committed pages must be what the generator makes from the committed data
        stale = [rel for rel, content in pages.items() if not (DOCS / rel).exists() or (DOCS / rel).read_text(encoding="utf-8") != content]
        if stale:
            sys.exit("out of date (run python3 tools/build-site.py and commit): " + ", ".join(stale))
        print("site is up to date")
        return
    for rel, content in pages.items():
        p = DOCS / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")
        print("wrote", rel, len(content.encode()) // 1024, "KB")


if __name__ == "__main__":
    main()
