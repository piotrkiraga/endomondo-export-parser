## Context

`fragments/head.html` currently sets `data-bs-theme` from `prefers-color-scheme` in an inline snippet before first paint. The navbar lives in `fragments/header.html` with the language switcher right-aligned. Static resources are served by Spring Boot's autoconfigured `/**` resource handling; the security permit-all list already covers `/webjars/**` but nothing under `/js/**`. Tests assert text, not markup.

## Goals / Non-Goals

**Goals:** instant, persistent, accessible manual theme control; automatic behavior unchanged until the user opts in; no flash of wrong theme on page load.

**Non-Goals:** server-side theme state; an explicit three-way auto/light/dark selector; icon fonts.

## Decisions

- **Resolution order: `localStorage.theme` → `prefers-color-scheme` → light** — the stored explicit choice wins; absent that, the current automatic behavior continues byte-for-byte. Stored value is `"light"` or `"dark"`, nothing else.
- **Keep the pre-paint inline snippet in `head.html`, move shared logic to `static/js/theme.js`** — the inline snippet must stay inline (it runs before first paint to avoid a theme flash and now also reads `localStorage`); the toggle handler and live `prefers-color-scheme` listener load as a small static file referenced from the head fragment. Alternative rejected: everything inline — duplicated logic in a fragment that pages include once anyway, but harder to read and grep.
- **Toggle button in the navbar, right of the language switcher: ☀ when dark (click → light), 🌙 when light (click → dark)** — the icon shows the mode you would switch *to*, matching the dominant convention. Unicode glyphs, `aria-label` from a new localized `theme.toggle` message key. Alternative rejected: Bootstrap Icons WebJar — a dependency for two glyphs.
- **Live OS-preference changes keep working only in auto mode** — the `matchMedia` change listener applies the OS theme only when no explicit choice is stored; after the user clicks the toggle, their choice is authoritative until they clear site data.
- **`/js/**` joins the security permit-all list** — same rationale as `/webjars/**`: presentation assets for anonymous pages.

## Risks / Trade-offs

- [Theme flash if the stored value is read after first paint] → the read stays in the inline head snippet, executed before the body renders.
- [Glyph rendering varies across platforms] → ☀/🌙 are in every modern system font set; the `aria-label` carries the meaning regardless.
- [Tests accidentally coupled to the navbar markup] → suite must pass unmodified; a markup-coupled failure means the change overstepped (same contract as the Bootstrap refresh).

## Migration Plan

Additive template/JS change; rollback = revert. No data, no server state.

## Open Questions

- None.
