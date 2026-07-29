## Context

`PhotoReportGenerator` renders each workout as `<section class="workout">...<h2>...</h2>...</section>` (see `render()`), with `data-path`-attribute-driven JS already established by `photo-report-copy-path` for the copy-path button, and one shared inline `<script>` block (`openLightbox`, `copyPath`, `legacyCopyPath`). The workout's `basename` is already known at render time (`group.basename()`) and already appears in the group's caption text, but not as a machine-readable attribute — nothing today lets client-side JS identify which `<section>` belongs to which workout.

Because the report has no server round-trip once rendered (must work opened straight from `file://`), any "remember this across sessions" feature has to live entirely in the browser. `localStorage` is the only such mechanism available without a build step or external library.

## Goals / Non-Goals

**Goals:**
- A workout's marked state is derived and applied entirely client-side after the static HTML loads, with no server involvement.
- The same mechanism works identically whether the file is opened from disk or served through the app.

**Non-Goals:**
- No cross-browser or cross-device sync of marked state (see proposal's Non-goals).
- No attempt to reconcile marked state with the migration ledger's actual "migrated" status.

## Decisions

- **One `<section class="workout">` gains a `data-basename` attribute** (attribute-escaped via the existing `escape()` helper, same as every other attribute in this file) so client-side JS can identify which section a `localStorage` entry belongs to. The basename is already rendered as visible text ("archive: ...") for cross-reference; this just also exposes it in a form JS can read without text-parsing.
- **A single `localStorage` key holds one JSON object mapping basename → `true`** (e.g. key `"endomondo-photo-report:uploaded"`, value `{"2015-04-11 11_38_17.0": true, ...}`), rather than one `localStorage` key per workout. One key keeps the report's storage footprint predictable and avoids namespace collisions with anything else that might use `localStorage` under the same origin (relevant when served through the running app, which shares its origin with the rest of the app's pages) — a `photo-report-mark-uploaded:<basename>`-per-key scheme was considered and rejected for exactly that collision risk plus the extra bookkeeping of enumerating/clearing many keys.
- **State is applied on load by a small init function appended to the existing `<script>` block**, not rendered server-side into each section's class, since the server has no way to know a browser's `localStorage` contents at generation time. The init function runs at the bottom of `<body>` (script placement already after all markup, matching the existing pattern), reads the stored JSON object once, and adds the "marked" CSS class to every `<section>` whose `data-basename` is a key in it.
- **Toggling calls one `toggleUploaded(basename, button)` function** that flips the entry in the stored JSON object (add/delete the key), re-serializes and writes it back with `localStorage.setItem`, and toggles the CSS class on the button's ancestor `<section>` directly (`button.closest('.workout')`) — avoiding a second full-page re-scan for a single toggle.
- **Visual treatment is a new CSS class in `ReportStylesUtil.CSS`** (e.g. `.workout.uploaded`), not inline styles, matching how the report already styles everything through the one shared stylesheet block.

## Risks / Trade-offs

- [`localStorage` under a `file://` origin behaves inconsistently across browsers — some browsers give each `file://` path its own isolated storage bucket, others restrict or disable it entirely] → the report is always regenerated to the same fixed path (`.../photo-report/index.html`), so within a single browser that supports `file://` storage (Chrome does, scoped per exact path), persistence is stable across regenerations. Documented as a known limitation, not solved further, consistent with the proposal's non-goal of only supporting one browser's storage.
- [Marked state and migration status can drift apart — a workout can be marked uploaded but not actually migrated, or vice versa] → intentional per the proposal's non-goals; the two are deliberately independent signals, not merged.

## Migration Plan

Purely additive: new markup, one new `data-basename` attribute, `localStorage`-backed JS, and one new CSS class. No persisted server-side data, no template file, nothing to roll back beyond a code revert.
