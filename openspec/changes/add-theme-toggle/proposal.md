## Why

The UI now follows the browser's light/dark preference automatically, but the user cannot override it: whatever the operating system says is what they get. A moon/sun switch in the navbar gives the user direct control — the standard modern pattern: automatic until the user chooses, then their choice wins and persists in that browser.

## What Changes

- A moon/sun toggle button in the navbar switches Bootstrap's color mode instantly, on every page.
- The explicit choice persists in the browser (`localStorage`) across pages and visits; while no choice has been made, the automatic `prefers-color-scheme` behavior continues, including reacting to live OS theme changes.
- The toggle is accessible: a localized label (English and Polish) for assistive technology; the icon is a symbol, not text.
- **Preserved behavior:** everything else — the automatic default for first-time visitors, light fallback without JavaScript, all functionality and text. The existing test suite must stay green unmodified.

## Non-goals

- No server-side persistence (no cookie, no account setting) — the theme is a per-browser presentation preference, invisible to the server.
- No third state in the UI (no explicit "auto" button); clearing the stored choice is browser-tooling territory.
- No icon library dependency — Unicode glyphs suffice for one button.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities
- `web-ui-presentation`: the "UI follows the browser color scheme" requirement is superseded by a user-controllable scheme — automatic preference-following plus a persistent manual override.

## Impact

- `fragments/head.html` — initialization honors the stored choice before falling back to `prefers-color-scheme`.
- `fragments/header.html` — the toggle button in the navbar.
- A small static JavaScript file (theme initialization + toggle), served from the jar; `/js/**` added to the security permit-all list (same reasoning as `/webjars/**`).
- `messages*.properties` — localized accessible label for the toggle.
- Existing tests — expected to pass unmodified.
