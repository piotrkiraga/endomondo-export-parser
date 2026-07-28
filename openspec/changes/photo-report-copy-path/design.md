## Context

`PhotoReportGenerator` builds a fully self-contained, offline-openable HTML file via `StringBuilder` (no Thymeleaf — this report has no dependency on the running app or its template engine, by explicit requirement, since it must open correctly straight from disk). It already has one inline `<script>` block (`openLightbox`, for click-to-enlarge) and one shared stylesheet (`ReportStylesUtil.CSS`). Each photo is rendered as `<figure><img src="..." onclick="openLightbox(this.src)">...`, where the `src` is a *relative* URL (`relativeHref`) so the image loads correctly whether the file is opened from disk or served through the app's own `/photo-report/**` mapping. The absolute local path itself — what a copy button needs — is available in Java as `captioned.photo().copy()` but isn't currently rendered anywhere on this report (the review page has a separate, similar absolute-path display via a readonly input).

## Goals / Non-Goals

**Goals:**
- One click copies a photo's real, absolute filesystem path to the clipboard.
- Works both opened straight from disk (`file://`) and served through the running app — the report's one hard requirement.

**Non-Goals:**
- No attempt to automate the actual attach step in Strava's UI — that's Strava's own page, outside this app's control.
- No redesign of the review page's existing click-to-select mechanism.

## Decisions

- **The absolute path travels through a `data-path` HTML attribute, not an inline JS string literal** — Windows paths contain backslashes (`C:\Users\...\photo.jpg`), which are invalid/misinterpreted inside a JS string literal (e.g. `onclick="copyPath('C:\Users\...')"` would corrupt on `\U`, `\p`, etc.). Putting the path in `data-path="..."` (escaped the same way every other attribute in this file already is, via the existing `escape()` helper) and reading it back with `this.dataset.path` in JS sidesteps JS-string escaping entirely — only HTML-attribute escaping applies, which this file already does correctly everywhere else.
- **`navigator.clipboard.writeText()` first, with a `document.execCommand('copy')` fallback via a temporary off-screen `<textarea>`** — the modern Clipboard API is preferred when available, but its availability under a `file://` origin (this report's primary use case) varies by browser/version; the legacy `execCommand` path (select a temporary textarea's content, copy, remove it) works even where the Clipboard API is blocked, so the button degrades to "still works" rather than "silently does nothing."
- **Brief inline visual feedback on click (e.g. the button's own text flips to "Copied!" for ~1.5s), not a separate toast/alert** — matches the report's existing minimal-JS style (`openLightbox` is a few lines, no framework); an `alert()` would be intrusive for something meant to be clicked repeatedly down a long list of photos.
- **One shared `copyPath(button)` function appended to the existing `<script>` block**, not a second `<script>` tag — keeps the report's script surface in one place, matching how `openLightbox` already lives there.

## Risks / Trade-offs

- [Clipboard API blocked under `file://` in some browsers] → mitigated by the `execCommand` fallback; both paths tested.
- [Absolute paths reveal local filesystem structure (username, folder layout) in the generated HTML] → already true today for the review page's equivalent readonly input, and the report itself already documents it's personal, git-ignored output — not a new exposure this change introduces.

## Migration Plan

Purely additive: new markup + one new JS function inside `PhotoReportGenerator`. No persisted data, no template file, nothing to roll back beyond a code revert.

## Open Questions

None blocking.
