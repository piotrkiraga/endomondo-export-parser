## Context

See proposal.md - Why. `ReportStylesUtil.CSS` (shared inline stylesheet for both offline reports) currently has:

```
.workout.migrated{border-color:#2a9d5c;background-color:var(--uploaded-bg)}
.workout.uploaded{border-color:#2a9d5c;background-color:var(--uploaded-bg)}
.workout.uploaded .mark-uploaded{color:#2a9d5c;border-color:#2a9d5c}
```

`.migrated` (workout has a recorded Strava activity id, applies to both reports) and `.uploaded` (local "photos physically attached" checklist mark, photo report only) are independent booleans that can both be true for the same workout, but currently render with the identical green (`#2a9d5c` / `--uploaded-bg`).

**Correction (2026-07-30):** the green (`#2a9d5c` / `--uploaded-bg`) is `.uploaded`'s original, correctly-chosen color — the `--uploaded-bg` variable name says so, and it predates `.migrated` entirely (shipped with the mark-as-uploaded feature first). `.migrated` is the one that later borrowed it. The fix therefore leaves `.uploaded` untouched and gives `.migrated` the new color instead — the reverse of this document's first draft, which had it backwards. `.migrated` is shared by both reports (`migration-report-status-indicator`), so this does change its color on the workout report too, not just the photo report; that's an accepted, deliberate consequence of the correction, not a scope expansion — the workout report has no competing indicator to collide with, so the color change there is cosmetic only.

## Goals / Non-Goals

**Goals:**
- `.workout.uploaded` and `.workout.migrated` are visually distinguishable from each other, including when both apply to the same card.
- The new color reads clearly in both the light and dark report themes, same as the existing palette.

**Non-Goals:**
- No redesign of the card layout, icons, or badge system — color only.
- No change to `.workout.uploaded`'s own color or treatment — it keeps its original green.

## Decisions

- **New CSS custom properties `--migrated-bg`/`--migrated-accent`, distinct from `--uploaded-bg`** — `--uploaded-bg` is `.uploaded`'s original, correctly-named variable and stays untouched. `.migrated` gets its own new variable instead of continuing to borrow `.uploaded`'s, making each class's color independently changeable and the naming honest: `--uploaded-bg`/`#2a9d5c` stays with `.uploaded` (its original, correct owner), `.migrated` gets `--migrated-bg`/`--migrated-accent`.
- **Purple accent (`#6f42c1` border, light-tinted background) for `.workout.migrated`, not another shade of green** — every existing green in this stylesheet already means "uploaded/confirmed" (`.uploaded`, `.action.upload`, the confirmed-gear styling elsewhere in the app); reusing a different green risks the same "it's still basically green" confusion this change exists to fix. Blue was considered but collides with `.action.manual` and the `--link` color already used for hyperlinks on the same card. Purple/indigo is otherwise unused in this stylesheet, so it reads as its own thing.
- **`.workout.uploaded .mark-uploaded`'s button color is unaffected** — it already matched the (unchanged) card accent and continues to.

## Risks / Trade-offs

- [`.migrated`'s color change is visible on the workout report too, not just the photo report, since the class is shared] → Accepted: the workout report has no second indicator to collide with `.migrated` there, so this is a cosmetic-only change on that report (still clearly distinguishes migrated from not-migrated, just in a new color), not a functional regression.
- [A workout that is both migrated and marked-uploaded shows a purple border plus a green background inherited from `.uploaded`'s rule ordering] → CSS declaration order in the source lists `.migrated` before `.uploaded`; since both set `background-color`, the later rule (`.uploaded`) wins when both classes are present, giving migrated-and-marked workouts the green background consistently. Acceptable: the scenario this change must satisfy is "both remain individually recognizable," not "both colors show simultaneously," and the border-color/background-color split still leaves a visual cue either way. Verified by the new task's test.

## Migration Plan

Purely additive/cosmetic: CSS-only change in one file, no data or behavior change, nothing to roll back beyond a code revert.
