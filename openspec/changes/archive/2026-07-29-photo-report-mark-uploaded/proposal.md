## Why

The photo report already helps a user copy a photo's path (`photo-report-copy-path`) and links each migrated workout to its Strava activity, but nothing tracks which workouts' photos have actually been attached on Strava yet — on a long list worked through over multiple sessions, that means re-checking captions or re-opening Strava to remember where you left off. Separately, clicking "view on Strava" today navigates the report tab away entirely, losing your scroll position in a long photo list.

## What Changes

- Each workout group on the photo report gets a "mark as uploaded" control. Clicking it flips that workout's marked state and applies a distinct visual treatment (e.g. a checked style) to the whole group.
- The marked state is stored in the browser's `localStorage`, keyed by the workout's `basename` (the same stable identifier the report already uses for `activityIdsByBasename`), so it survives closing/reopening the report and regenerating it later in the same browser.
- The existing "view on Strava" link now opens in a new browser tab (`target="_blank" rel="noopener"`) instead of navigating the report away.

## Capabilities

### New Capabilities
- `photo-report-mark-uploaded`: the per-workout "mark as uploaded" control and its `localStorage` persistence on the photo report.

### Modified Capabilities
- `migration-photo-report`: the "view on Strava" link's requirement ("The report enables one-step manual photo attachment") changes from a same-tab navigation to opening in a new tab.

## Non-goals

- No server-side/persisted-on-disk tracking of upload state — `localStorage` only, scoped to one browser. Re-opening the report in a different browser or after clearing site data starts unmarked.
- No per-photo marking — granularity is per-workout, matching how photos are already grouped and how a user typically attaches a whole workout's photos in one Strava editing session.
- No interaction with the migration ledger or the actual "migrated/not migrated" (`activityIdsByBasename`) distinction — this is a separate, purely client-side bookkeeping aid for the manual-attachment step, not a change to migration status.

## Characterization vs. Change

- Preserved: photo grouping, captions, the lightbox, the copy-path button, EXIF geotagging, and the report's fully self-contained/offline nature are all unchanged.
- Preserved: the "pending migration" text before a workout has a Strava activity id is unchanged; the mark-as-uploaded control does not depend on or alter migration status.
- Changed: `PhotoReportGenerator` adds one control and its `localStorage` read/write logic to the existing inline `<script>` block, and adds `target="_blank" rel="noopener"` to the existing Strava activity link.

## Impact

- `service/PhotoReportGenerator` (per-workout markup, `localStorage` JS logic, `activityLink` method gains `target`/`rel`)
- No changes to `PhotoGeotagger`, `MigrationLedger`, or any Strava-facing code
