## Why

The standalone photo and workout reports currently render dark or light purely by following the browser's `prefers-color-scheme` media query — confirmed live: a user who explicitly set the app to light mode (via the navbar toggle, `localStorage.theme === "light"`) still sees the report render dark, because their OS/browser happens to report a dark system preference and the report has no code that reads the app's stored choice at all. The app's own pages already resolve this correctly (stored explicit choice, else `prefers-color-scheme`, else light) — the reports just never got that logic.

## What Changes

- When a report (`PhotoReportGenerator` or `WorkoutReportGenerator`'s output) is opened through the running app — same origin as the app's own pages — it reads the same `theme` `localStorage` key the navbar toggle already writes, and applies it, taking priority over `prefers-color-scheme`.
- When the report is opened directly from disk (`file://`), there is no access to that origin's `localStorage`; it falls back to `prefers-color-scheme`-only behavior, same as today — an explicit, accepted limitation, not a bug to chase further.
- No change to the navbar toggle itself, its storage key, or any app page's behavior.

## Capabilities

### Modified Capabilities
- `web-ui-presentation`: the "color scheme is automatic with a persistent user override" requirement extends its scope to the standalone reports (when served through the app), with the `file://`-vs-served distinction stated explicitly.

## Non-goals

- No mechanism to sync the explicit theme choice into a report opened from disk — `localStorage` is origin-scoped and a `file://` document has no access to the app's origin. This is a hard platform limit, not a design choice to revisit.
- No new UI control on the reports themselves (no theme toggle button on the report) — this only makes the reports respect the choice already made on the app's pages.
- No change to what "light"/"dark" actually look like (the existing CSS variable values in `ReportStylesUtil` are unchanged) — only which one gets selected.

## Characterization vs. Change

- Preserved: `prefers-color-scheme`-only behavior when no explicit choice is stored, or when opened from `file://` — unchanged in both cases.
- Preserved: the app's own `theme.js`, its storage key, and its resolution order are the source of truth; the reports read from it, they don't duplicate or reinvent the logic.
- Changed: `ReportStylesUtil.CSS` gains a `data-theme` attribute selector (in addition to the existing `prefers-color-scheme` media query) so JS can force a mode; both `PhotoReportGenerator` and `WorkoutReportGenerator` gain a small inline script that reads `localStorage.theme` (best-effort, same-origin only) and sets that attribute on page load.

## Impact

- `util/ReportStylesUtil` (CSS: add `data-theme` attribute overrides alongside the existing media query)
- `service/PhotoReportGenerator` (add theme-read/apply logic to its existing shared `<script>` block)
- `service/WorkoutReportGenerator` (currently has no `<script>` block at all — this change gives it its first one, just for this)
- No changes to `static/js/theme.js`, any Thymeleaf template, or the app's own page rendering
