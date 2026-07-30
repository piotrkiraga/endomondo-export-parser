## Why

The photo report's "mark as uploaded" indicator (`.workout.uploaded`, local checklist state) and the migrated-to-Strava indicator (`.workout.migrated`, shared with the workout report) render with the exact same card border/background color in `ReportStylesUtil`. Once most workouts in an archive are migrated, every card shows the same green tint regardless of its actual mark-as-uploaded state, so the mark-as-uploaded treatment — which is supposed to be a distinct, whole-group visual signal per its own spec — becomes indistinguishable from the migrated one. Found manually testing the photo report (2026-07-30); tracked as GitHub issue #23.

## What Changes

- `.workout.migrated`'s card accent (border/background) in `ReportStylesUtil` changes to a color distinct from `.workout.uploaded`'s, so the two independent states never look identical.
- No change to `.workout.uploaded` or the `.mark-uploaded` button's "marked" styling — the green (`--uploaded-bg`) was `.uploaded`'s original, correctly-chosen color (it predates `.migrated` reusing it) and stays exactly as it was.
- Because `.workout.migrated` is shared by both reports (`migration-report-status-indicator`), its new color is visible on the workout report too, not just the photo report — a deliberate, accepted consequence (see design.md), not a scope expansion: the workout report has no second indicator for it to collide with, so this is cosmetic there.

**Correction (2026-07-30):** this proposal originally had the direction backwards — it described changing `.uploaded`'s color and leaving `.migrated` untouched. The green belongs to `.uploaded`, not `.migrated`; this section now reflects the corrected direction that was actually implemented.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `photo-report-mark-uploaded`: the marked-workout visual treatment gains a requirement that it stays visually distinguishable from the report's separate migrated-workout treatment, and the treatment's color changes accordingly.

## Non-goals

- No change to which workouts count as "migrated" or how that's computed — unaffected.
- No change to the icon/text treatment on either report, or to `WorkoutReportGenerator`'s or `PhotoReportGenerator`'s markup — color only.
- No new indicator, badge, or layout — this is a color/treatment adjustment to an existing indicator, not a new visual language.
- No change to the mark-as-uploaded control's behavior (toggling, `localStorage` persistence, per-workout independence) — unaffected, since `.uploaded` itself doesn't change.

## Characterization vs. Change

- Preserved: `.workout.uploaded`'s accent color and the `.mark-uploaded` button's matching "marked" color, unchanged.
- Preserved: mark-as-uploaded's toggle behavior, `localStorage` persistence, and per-workout independence (`photo-report-mark-uploaded`'s existing requirements).
- Preserved: the migrated treatment's icon and the fact that it's a border/background accent — only its specific color changes.
- Changed: `.workout.migrated`'s accent color in `ReportStylesUtil`, so it's visually distinct from `.workout.uploaded` even when both apply to the same card. Visible on both reports, since the class is shared.

## Impact

- `util/ReportStylesUtil` (color values for `.workout.migrated`)
- No changes to `service/PhotoReportGenerator`'s or `service/WorkoutReportGenerator`'s markup, or any Strava-facing code
