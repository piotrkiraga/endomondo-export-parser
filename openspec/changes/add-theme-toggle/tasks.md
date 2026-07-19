## 1. Theme script and initialization

- [ ] 1.1 Create `static/js/theme.js` (toggle handler: flip `data-bs-theme`, persist `"light"`/`"dark"` to `localStorage.theme`, update the toggle glyph; `matchMedia` change listener applying the OS preference only when no choice is stored); extend the inline snippet in `fragments/head.html` to resolve `localStorage.theme` → `prefers-color-scheme` → light before first paint, and reference the script (verify: script served with HTTP 200 anonymously)
- [ ] 1.2 Add `/js/**` to `AUTHENTICATION_NOT_REQUIRED` in `WebSecurityConfiguration` (verify: `curl -o /dev/null -w "%{http_code}" http://localhost:8080/js/theme.js` returns 200 without credentials)

## 2. Navbar toggle

- [ ] 2.1 Add the toggle button to `fragments/header.html` right of the language switcher: ☀ shown in dark mode, 🌙 in light mode, `aria-label` from a new `theme.toggle` key added to all three message bundles (English and Polish values) (verify: button renders on home, upload, and error pages in both languages)

## 3. Verification

- [ ] 3.1 Full suite passes unmodified (verify: `mvnw test` green, `git diff` shows no test files touched)
- [ ] 3.2 Rebuild jar and walk in the browser: automatic mode still follows OS preference; clicking the toggle flips the theme instantly; the choice survives reload and navigation and overrides the OS preference; then `openspec validate add-theme-toggle` and commit (verify: all behaviors observed, validation passes)
