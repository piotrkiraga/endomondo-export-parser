## Why

The workout report and photo report already show, per workout, whether it's migrated (a "view on Strava" link) or not (plain text "pending migration") — but that's the only signal, and it's easy to miss while scanning a long list. Both reports already color-code the *planned action* (upload/manual/skip get distinct colored badges) but nothing distinguishes *migrated* from *not yet migrated* at a glance — you have to read the text of every single card.

## What Changes

- Both reports (`WorkoutReportGenerator`, `PhotoReportGenerator`) gain a visual migrated/not-migrated distinction on each workout's card: a distinct left-border/background tint plus a small icon next to the Strava link text, applied consistently to both reports since they already intentionally share one look (`ReportStylesUtil`).
- Scope is strictly the binary already exposed by the existing "activityId present or not" check — this does not add or surface the ledger's finer-grained skipped/failed/pending distinction, which is a separate concern not part of this ask.

## Capabilities

### New Capabilities
- `migration-report-status-indicator`: the visual migrated/not-migrated treatment shared by both offline reports.

### Modified Capabilities
(none — the underlying migrated/not-migrated data (`activityIdsByBasename`) is unchanged; this only changes how existing data is rendered)

## Non-goals

- No change to which workouts count as "migrated" (still: has a recorded Strava activity id) — the known cosmetic ledger gap (a duplicate-rejection recorded as `FAILED` despite genuinely being on Strava) is unaffected and still shows as "not migrated" here, same as today.
- No distinct treatment for skipped vs. failed vs. pending — those all remain "not migrated" visually, matching the binary the user asked for.
- No change to the interactive review page (`/migration/review`) — it already shows an explicit Migrate/Skip decision per workout with its own status; this proposal is scoped to the two offline reports only.

## Characterization vs. Change

- Preserved: both reports' existing action badges (upload/manual/skip), activity link text/URL, and all other content are unchanged.
- Preserved: `activityIdsByBasename`'s meaning and the "has activity id = migrated" rule are unchanged.
- Changed: `ReportStylesUtil.CSS` gains a new class for the migrated/not-migrated card treatment; both generators' per-workout section markup applies it and adds the icon.

## Impact

- `util/ReportStylesUtil` (new CSS)
- `service/WorkoutReportGenerator`, `service/PhotoReportGenerator` (apply the new class/icon per card)
- No changes to any Strava-facing code, the ledger, or the review page
