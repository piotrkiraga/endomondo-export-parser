## Context

Both reports render color via CSS custom properties on `:root`, set once for light and overridden inside `@media (prefers-color-scheme:dark)`. Neither report currently has any code that reads `localStorage` before this change: `PhotoReportGenerator` has one shared `<script>` block at the end of `<body>` (`openLightbox`, `copyPath`/`legacyCopyPath`, the mark-as-uploaded functions from `photo-report-mark-uploaded`); `WorkoutReportGenerator` has no `<script>` block at all. The app's own `theme.js` (`static/js/theme.js`) is the source of truth for the `theme` `localStorage` key and its `'light'`/`'dark'` values — this change reads that key, it does not reimplement its logic.

## Goals / Non-Goals

**Goals:**
- The theme actually applied is decided before the report's first paint, so there's no visible flash of the wrong mode.
- Zero behavior change when there's no explicit choice to read (unset key, or opened from `file://`) — falls straight through to the existing `prefers-color-scheme` behavior.

**Non-Goals:**
- No live update if the user changes the app's theme *while* a report tab is already open — the report reads the stored value once, on its own load. Matching this precisely would require the report to poll or listen for storage events for a document that's otherwise fully static; not worth the complexity for a report you regenerate and reopen per session.

## Decisions

- **A `data-theme` attribute on `<html>`, read via CSS attribute selectors, not a second media query.** `:root[data-theme="dark"]{...}` and `:root[data-theme="light"]{...}` are added alongside the existing `:root{...}` (light default) and `@media (prefers-color-scheme:dark){:root{...}}` blocks. An attribute selector on `:root` has higher specificity than the bare `:root` inside the media query, so when JS sets the attribute, it wins regardless of source order or system preference — when JS doesn't set it (no stored choice, or `file://`), neither attribute selector matches anything and the existing media-query behavior is exactly what runs, unchanged.
- **The theme-read script goes in `<head>`, not the existing end-of-`<body>` script block.** The other inline scripts (`openLightbox`, `copyPath`, mark-as-uploaded) are behavior triggered by a later user click, so their position at the bottom of the page doesn't matter. This one decides the *initial* paint — placed at the bottom, the page would flash the wrong colors and then flip once the script finally runs. A tiny synchronous script placed right after `<style>` in `<head>`, before `<body>` starts rendering, avoids that flash entirely. It stays a separate, minimal script (`try { localStorage.getItem('theme') ... } catch {}`) rather than merging into the existing body-end block.
- **Wrapped in `try { } catch { }`, silently doing nothing on failure.** Reading `localStorage` from a `file://` document throws in some browsers rather than just returning `null` (unlike `sessionStorage` semantics) — matching how `copyPath`'s Clipboard-API fallback and `mark-uploaded`'s `loadUploaded()` already treat storage access as best-effort, not guaranteed.
- **Duplicated between `PhotoReportGenerator` and `WorkoutReportGenerator`, not extracted into a shared constant.** `WorkoutReportGenerator` has no existing script infrastructure to hook into, and the two generators already independently build their own `<script>` content as plain Java string concatenation with no shared script-string mechanism between them (only the CSS is shared, via `ReportStylesUtil.CSS`). The theme script is ~1 line of logic; introducing a new shared-script utility class for that one line would be more machinery than the duplication it avoids, and it matches this codebase's already-stated convention of duplicating small pieces across the two self-contained reports rather than linking them (see `ReportStylesUtil`'s own doc comment on `.detail-list`/`.detail-row`).

## Risks / Trade-offs

- [A user opens the report from disk after having explicitly chosen a theme in the app] → by design, falls back to `prefers-color-scheme` — this is the accepted, documented limitation from the proposal's non-goals, not something this design tries to work around.
- [Attribute-selector CSS added without touching the existing media-query block could regress if the two ever disagree on a variable] → both blocks are written to set the exact same variable set with the exact same values per mode, so there's only one place to update either — a value change to "dark" must be made in both `@media (prefers-color-scheme:dark)` and `:root[data-theme="dark"]`; this asymmetry already exists nowhere else in the file, so it's called out here rather than silently duplicated.

## Migration Plan

Purely additive: new CSS attribute selectors, one new small `<head>` script per generator (new to `WorkoutReportGenerator`, additional to `PhotoReportGenerator`'s existing block). No persisted data, no template file, nothing to roll back beyond a code revert.
