## Context

Both offline reports (`WorkoutReportGenerator`, `PhotoReportGenerator`) build raw HTML via `StringBuilder` (a known, already-flagged departure from this app's Thymeleaf-everywhere rule, accepted here because both reports must stay fully self-contained files openable straight from disk, with no dependency on the running app's template engine or static assets). Each already renders one `<section class="workout ...">` card per workout, with an `.action` badge (green=upload, blue=manual, tan=skip via `.workout.skip`'s border) and an `activityLink()` helper that renders either a clickable "view on Strava" link (migrated) or the plain text "pending migration" (not). `ReportStylesUtil.CSS` is the one shared, inline (no external font/CDN) stylesheet both generators reuse.

## Goals / Non-Goals

**Goals:**
- Make migrated vs. not-migrated visually obvious per card without reading text, on both reports, using the same treatment.
- Stay fully self-contained (no external icon font/CDN) — these files must render correctly opened straight from disk, offline.

**Non-Goals:**
- No new status taxonomy beyond the existing binary (has an activity id, or doesn't) — skipped/failed/pending all continue to render identically to today ("pending migration"), just now also getting the "not migrated" card treatment.

## Decisions

- **A new `.workout.migrated` modifier class, mirroring the existing `.workout.skip` pattern** — `.workout.skip` already proves the "modifier class changes the card's left border" pattern works and is already in `ReportStylesUtil.CSS`; `.migrated` follows the same shape (`border-color` + a faint background tint) rather than inventing a new visual language. Not-migrated stays the current default card look — no new class needed for that state, matching how "not skip" today just means the class is absent.
- **Green accent for `.migrated`**, reusing the same `#2a9d5c` already used for the "upload" action badge — that color already reads as "success"/"done" in both reports, so reusing it keeps the palette consistent rather than introducing a third accent color.
- **Plain Unicode glyphs, not an icon font or inline SVG** — a checkmark (`✓`) prepended to the "view on Strava" link, an open-circle (`○`) prepended to "pending migration" — both self-contained (already achievable in a Java string literal, no new asset/dependency), and both remain meaningful to a screen reader since they sit directly beside their existing text rather than replacing it.
- **`activityLink()` in each generator returns the icon+text+class together** — each generator already has the `activityId`/`Optional<String>` check right where it calls `activityLink()`; extending that one method to also hand back whether the card is "migrated" (for the section's class list) keeps the icon and the card-level class derived from the exact same check, so they can't disagree with each other.
- **Applied identically in both generators** — per this project's established precedent of keeping the two reports visually identical (they already share `ReportStylesUtil`, the same `.detail-list` component, and the same dark-mode handling), this ships in both `WorkoutReportGenerator` and `PhotoReportGenerator` in the same change, not just one.

## Risks / Trade-offs

- [Green accent could read as "good" even for the two known cosmetically-`FAILED`-but-actually-present ledger entries] → out of scope per the proposal's non-goals: those entries have no recorded activity id, so they correctly render as "not migrated" here, same as they do today — this change doesn't touch that known, separately-accepted gap.
- [Existing generator tests assert on rendered HTML strings] → new assertions are additive (checking for the new class/glyph's presence), no existing assertion needs to change since the existing link text/href are untouched.

## Migration Plan

Purely additive CSS + generator markup change. No persisted data, no template file (these reports have none), nothing to roll back beyond a code revert.

## Open Questions

None blocking.
