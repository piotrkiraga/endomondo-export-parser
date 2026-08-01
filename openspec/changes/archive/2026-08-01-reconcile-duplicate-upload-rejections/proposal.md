## Why

The migration ledger records a workout as `FAILED` when Strava rejects its upload as a duplicate but the rejection response carries no `activity_id` — even though the workout is genuinely already on Strava from an earlier run. This is a known, previously accepted gap (see `openspec/specs/strava-migration/spec.md`'s "A duplicate rejection without a returned activity id is recorded as failed, not silently reconciled" scenario, and issue #46): the original design bet entirely on `activity_id` being present on a duplicate rejection ("community-observed, not contractual" per `migrate-to-strava/design.md`), and that bet didn't hold — at least two real ledger entries hit exactly this case. Reports then show a genuinely-migrated workout as "not migrated," which is a real data-correctness problem, not a cosmetic one.

## What Changes

- When an upload is rejected as a duplicate without an `activity_id`, the migration now reads the athlete's own activity list back from Strava (a narrow time-window query around the workout's known start time) and looks for a single activity matching that start time and, when the workout's distance is known, a close distance — the same "read back, don't trust the write call" posture already used for the old-bike gear correction.
- If exactly one activity matches, its id is recorded as the migrated activity (ledger `DONE`), same as a normal successful upload.
- If no single activity satisfies both checks, the workout still falls back to today's behavior: recorded `FAILED`. This never guesses a wrong activity id — an unconfirmed rejection surfaces as a failure, not a silent (and possibly wrong) reconciliation.
- **Non-goal**: no change to duplicate detection for `INPUT_MANUAL` (create) workouts — they have no `external_id` and no duplicate-rejection response shape to reconcile; the ledger's pending/done write-order is their only guard, unchanged.
- **Non-goal**: no change to genuine upload failures (bad file, auth, rate limit, etc.) — those are unaffected and continue to fail exactly as today.

## Capabilities

### Modified Capabilities
- `strava-migration`: a duplicate-upload rejection without a returned activity id is now reconciled via a live Strava read-back instead of unconditionally recorded as failed.

## Impact

- New `StravaClient.listActivities(after, before)` — a narrowly-windowed read of `GET /athlete/activities`, using the `activity:read_all` scope this app already requests (no new OAuth scope, no reconnect required).
- New `StravaActivitySummaryDto` (id, start_date, distance) for that response shape.
- New `DuplicateActivityResolver` service, mirroring `ConfirmedGearResolver`'s architecture: matching/reconciliation logic lives here, `StravaClient` stays a thin API wrapper.
- `MigrationExecutor.uploadAndPoll`: on a duplicate rejection (`StravaUploadResultDto.failed()`), calls the resolver before giving up and marking the workout failed.
