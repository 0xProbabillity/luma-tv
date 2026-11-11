/* shangOS website script. Optional: every page is readable without it. No network calls, no cookies.
   Progressive enhancements: theme toggle, mobile menu, copy buttons, OS tabs, contents highlighting. */
(() => {
  "use strict";
  const root = document.documentElement;
  root.classList.add("js");

  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (_) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (_) { /* private mode: fine */ } },
  };
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => Array.from(el.querySelectorAll(s));

  /* Theme: light / dark, remembered on this device only. */
  const themeBtn = $("[data-theme-toggle]");
  const isDark = () => (root.dataset.theme ? root.dataset.theme === "dark" : matchMedia("(prefers-color-scheme: dark)").matches);
  const syncTheme = () => {
    if (!themeBtn) return;
    themeBtn.setAttribute("aria-pressed", String(isDark()));
    themeBtn.setAttribute("aria-label", isDark() ? "Switch to light theme" : "Switch to dark theme");
  };
  themeBtn?.addEventListener("click", () => {
    const next = isDark() ? "light" : "dark";
    root.dataset.theme = next;
    store.set("shangos-theme", next);
    syncTheme();
  });
  syncTheme();

  /* Header gets a hairline once the page has scrolled. */
  const header = $(".site-header");
  if (header && "IntersectionObserver" in window) {
    const sentinel = document.createElement("div");
    sentinel.setAttribute("aria-hidden", "true");
    sentinel.style.cssText = "position:absolute;top:0;height:1px;width:1px;pointer-events:none";
    document.body.prepend(sentinel);
    new IntersectionObserver(([e]) => header.classList.toggle("is-stuck", !e.isIntersecting)).observe(sentinel);
  }

  /* Mobile menu: button, Escape, outside click, link click. */
  const navBtn = $("[data-nav-toggle]");
  const nav = $("#site-nav");
  if (navBtn && nav) {
    const set = (open) => {
      nav.classList.toggle("is-open", open);
      navBtn.setAttribute("aria-expanded", String(open));
      navBtn.setAttribute("aria-label", open ? "Close menu" : "Open menu");
    };
    navBtn.addEventListener("click", () => set(!nav.classList.contains("is-open")));
    nav.addEventListener("click", (e) => { if (e.target.closest("a")) set(false); });
    document.addEventListener("keydown", (e) => { if (e.key === "Escape" && nav.classList.contains("is-open")) { set(false); navBtn.focus(); } });
    document.addEventListener("click", (e) => { if (!e.target.closest(".site-header")) set(false); });
    matchMedia("(min-width: 48rem)").addEventListener("change", () => set(false));
  }

  /* Copy buttons: copy the text of the code block they sit in. Prompts and comments are marked
     with .p and .c and are left out, so what lands on the clipboard can be pasted as it is. */
  const copyText = (term) => {
    const code = $("code", term).cloneNode(true);
    $$(".p, .c, .o", code).forEach((n) => n.remove());
    return code.textContent.split("\n").map((l) => l.replace(/\s+$/, "")).join("\n").replace(/^\n+|\n+$/g, "").replace(/\n{2,}/g, "\n");
  };
  const live = document.createElement("div");
  live.className = "vh";
  live.setAttribute("role", "status");
  document.body.append(live);
  document.addEventListener("click", async (e) => {
    const btn = e.target.closest(".copy");
    if (!btn) return;
    const term = btn.closest(".term");
    const text = copyText(term);
    try {
      await navigator.clipboard.writeText(text);
    } catch (_) {
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.style.cssText = "position:fixed;opacity:0";
      document.body.append(ta);
      ta.select();
      try { document.execCommand("copy"); } catch (__) { /* nothing more to try */ }
      ta.remove();
    }
    const old = btn.textContent;
    btn.textContent = "Copied";
    btn.dataset.state = "done";
    live.textContent = "Copied to clipboard";
    setTimeout(() => { btn.textContent = old; delete btn.dataset.state; live.textContent = ""; }, 1800);
  });

  /* Tabs for macOS / Linux / Windows. Picks the visitor's system first; remembers a manual choice. */
  const detectOS = () => {
    const p = ((navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || navigator.userAgent || "").toLowerCase();
    if (p.includes("win")) return "windows";
    if (p.includes("mac") || p.includes("iphone") || p.includes("ipad")) return "mac";
    return "linux";
  };
  $$("[data-tabs]").forEach((group) => {
    const tabs = $$('[role="tab"]', group);
    const panels = tabs.map((t) => document.getElementById(t.getAttribute("aria-controls")));
    const select = (i, focus) => {
      tabs.forEach((t, n) => {
        const on = n === i;
        t.setAttribute("aria-selected", String(on));
        t.tabIndex = on ? 0 : -1;
        panels[n].hidden = !on;
      });
      if (focus) tabs[i].focus();
    };
    const saved = store.get("shangos-os") || detectOS();
    const start = Math.max(0, tabs.findIndex((t) => t.dataset.os === saved));
    select(start, false);
    tabs.forEach((t, i) => {
      t.addEventListener("click", () => {
        store.set("shangos-os", t.dataset.os);
        $$("[data-tabs]").forEach((g) => g !== group && g.dispatchEvent(new CustomEvent("shangos:os", { detail: t.dataset.os })));
        select(i, false);
      });
      t.addEventListener("keydown", (e) => {
        const k = { ArrowRight: 1, ArrowLeft: -1 }[e.key];
        if (k) { e.preventDefault(); select((i + k + tabs.length) % tabs.length, true); }
        if (e.key === "Home") { e.preventDefault(); select(0, true); }
        if (e.key === "End") { e.preventDefault(); select(tabs.length - 1, true); }
      });
    });
    group.addEventListener("shangos:os", (e) => { const i = tabs.findIndex((t) => t.dataset.os === e.detail); if (i >= 0) select(i, false); });
  });

  /* Contents list: mark the section being read. */
  const toc = $(".toc");
  if (toc && "IntersectionObserver" in window) {
    const links = $$("a[href^='#']", toc);
    const byId = new Map(links.map((a) => [a.getAttribute("href").slice(1), a]));
    const seen = new Set();
    const io = new IntersectionObserver((entries) => {
      entries.forEach((e) => (e.isIntersecting ? seen.add(e.target.id) : seen.delete(e.target.id)));
      const first = links.find((a) => seen.has(a.getAttribute("href").slice(1)));
      links.forEach((a) => a.removeAttribute("aria-current"));
      if (first) first.setAttribute("aria-current", "true");
    }, { rootMargin: "-20% 0px -65% 0px" });
    byId.forEach((_, id) => { const s = document.getElementById(id); if (s) io.observe(s); });
    // On small screens the contents is a closed <details>; keep it open on wide screens.
    const d = $("details.toc");
    if (d) { const m = matchMedia("(min-width: 64rem)"); const f = () => { d.open = m.matches; }; f(); m.addEventListener("change", f); }
  }
})();
