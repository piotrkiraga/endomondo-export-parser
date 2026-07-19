## Context

Five templates (`home.html`, `upload.html`, `error.html`, `fragments/header.html`, `fragments/footer.html`, `fragments/messages-errors.html`) each carry their own `<head>` with a `stackpath.bootstrapcdn.com` Bootstrap 4.4.1 CSS link; no Bootstrap JavaScript is loaded anywhere (nothing currently needs it). Markup uses Bootstrap 4 idioms removed in 5: `form-group`, `form-control-file`. Spring Boot's autoconfigured resource handling already maps `/webjars/**` (visible in the startup log). All 18 tests assert rendered *text*, not markup classes. The upload page is reachable anonymously; static resources are covered by the existing permit-all rules for the tested pages.

## Goals / Non-Goals

**Goals:** current Bootstrap, self-contained jar, one shared head, visibly modern chrome (navbar, cards, color modes) — zero behavior change.

**Non-Goals:** custom design system, SCSS pipeline, icon fonts, client-side interactivity.

## Decisions

- **WebJar (`org.webjars:bootstrap:5.3.x`) over CDN** — the jar becomes self-contained (works offline, no third-party runtime dependency); Spring Boot serves `/webjars/**` out of the box. Versioned URLs (`/webjars/bootstrap/<version>/...`) with the version in exactly one place (the shared head fragment) — no `webjars-locator` dependency for one library. Verify the exact latest 5.3.x on Maven Central at apply time. Alternative rejected: keeping a (newer) CDN link — leaves the offline gap and an external dependency for a personal tool.
- **Shared `fragments/head.html` fragment parameterized by title** — removes five duplicated `<head>` blocks; the Bootstrap version string lives only there. Templates keep their own `<body>` structure.
- **Navbar in `fragments/header.html`** — Bootstrap 5 `navbar` with app name as brand, Home/Upload as nav links, language switcher right-aligned. Same links, same `?lang=` mechanism, same message keys — presentation only. No collapse/hamburger JavaScript needed at this page count; if the collapse toggle is used, load Bootstrap's bundled JS in the head fragment.
- **Bootstrap 5.3 color modes with an inline preference snippet** — a few lines in the head fragment set `data-bs-theme` from `prefers-color-scheme` before first paint. Modern feel for free, no dependencies, degrades to light mode without JavaScript.
- **Markup migration by idiom, not redesign** — `form-group` → spacing utilities + `form-label`; `form-control-file` → `form-control`; upload form and workout summary each get a card; alerts stay `alert alert-danger|info` (Bootstrap 5 kept these classes — message-assertion tests unaffected).
- **Tests are the behavioral contract** — the suite must pass unmodified. If any assertion breaks on markup (not text), that is a signal the change went beyond presentation; stop and reconsider rather than adapt the test.

## Risks / Trade-offs

- [Bootstrap 5 visual regressions on untested pages (error page)] → manual walk of all pages in the browser at apply time, both languages, light and dark.
- [WebJar path typo yields silent 404 → unstyled page] → smoke-test the CSS URL directly (`curl -o /dev/null -w "%{http_code}"`) as part of verification.
- [Security config might not cover `/webjars/**` for anonymous pages] → verify anonymously with curl; if blocked, add `/webjars/**` to the permit-all matcher — a config change, but presentation-motivated and spec-invisible.

## Migration Plan

Single change, additive dependency + template edits; rollback = revert. No data, no API changes.

## Open Questions

- None.
