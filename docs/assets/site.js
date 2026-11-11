/* shangOS — Native Client Script */
(() => {

  const root = document.documentElement;

  /* -------- 1. Theme Toggle System -------- */
  const themeBtn = document.getElementById('theme-toggle');
  if (themeBtn) {
    themeBtn.addEventListener('click', () => {
      const isDark = root.getAttribute('data-theme') === 'dark';
      if (isDark) {
        root.removeAttribute('data-theme');
        try { localStorage.setItem('shangos-theme', 'light'); } catch (e) {}
      } else {
        root.setAttribute('data-theme', 'dark');
        try { localStorage.setItem('shangos-theme', 'dark'); } catch (e) {}
      }
    });
  }

  /* -------- 2. Mobile Navigation Drawer -------- */
  const navToggle = document.getElementById('nav-toggle');
  const siteNav   = document.getElementById('site-nav');

  if (navToggle && siteNav) {
    navToggle.addEventListener('click', () => {
      const open = siteNav.classList.toggle('open');
      navToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    });

    document.addEventListener('click', (e) => {
      if (!siteNav.contains(e.target) && !navToggle.contains(e.target)) {
        siteNav.classList.remove('open');
        navToggle.setAttribute('aria-expanded', 'false');
      }
    });

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && siteNav.classList.contains('open')) {
        siteNav.classList.remove('open');
        navToggle.setAttribute('aria-expanded', 'false');
        navToggle.focus();
      }
    });
  }

  /* -------- 3. Copy-to-Clipboard with Micro-feedback -------- */
  const checkIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>';
  const copyIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>';

  document.querySelectorAll('.copy-btn, .copy-btn-inline').forEach((btn) => {
    btn.addEventListener('click', () => {
      let text = btn.getAttribute('data-copy');
      if (!text) {
        const wrap = btn.closest('.code-wrap, .hero-cli-card');
        const source = wrap ? wrap.querySelector('pre, .term, code') : null;
        if (source) {
          text = source.innerText.replace(/^\s*\$\s?/gm, '').trim();
        }
      }
      if (!text) return;

      const triggerDone = () => {
        btn.classList.add('copied');
        const originalHTML = btn.innerHTML;
        btn.innerHTML = `${checkIcon} <span>Copied</span>`;
        setTimeout(() => {
          btn.classList.remove('copied');
          btn.innerHTML = originalHTML;
        }, 2200);
      };

      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(triggerDone).catch(() => fallback(text, triggerDone));
      } else {
        fallback(text, triggerDone);
      }
    });
  });

  function fallback(text, doneCallback) {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    document.body.appendChild(ta);
    ta.select();
    try {
      document.execCommand('copy');
      doneCallback();
    } catch (e) {}
    document.body.removeChild(ta);
  }

  /* -------- 4. Documentation TOC Active Scroll-Spy -------- */
  (function initTocSpy() {
    const toc = document.querySelector('.doc-toc');
    if (!toc) return;
    const links = Array.from(toc.querySelectorAll('.toc-list a'));
    if (!links.length || !('IntersectionObserver' in window)) return;

    const targets = links
      .map((a) => {
        const href = a.getAttribute('href') || '';
        return href.startsWith('#') ? document.getElementById(href.slice(1)) : null;
      })
      .filter(Boolean);

    if (!targets.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const id = entry.target.id;
            links.forEach((link) => {
              const matches = link.getAttribute('href') === `#${id}`;
              link.classList.toggle('active', matches);
            });
          }
        });
      },
      { rootMargin: '-70px 0px -70% 0px', threshold: 0 }
    );

    targets.forEach((target) => observer.observe(target));
  })();
})();
