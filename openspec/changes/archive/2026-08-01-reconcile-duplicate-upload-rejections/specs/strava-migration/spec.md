## MODIFIED Requirements

### Requirement: Tracked workouts upload as TCX with idempotent external ids
For workouts whose source is a GPS track (not `INPUT_MANUAL`), the migration SHALL upload the paired TCX file via the Strava upload API with a deterministic `external_id` derived from the workout filename, poll the upload until processed, and record the resulting activity id in the ledger. Re-running the migration SHALL NOT create duplicates for workouts already in the ledger or rejected by Strava as duplicates. When Strava rejects an upload as a duplicate without returning an `activity_id`, the migration SHALL attempt to recover the real activity id by reading the athlete's own activity list back from Strava and matching on an exact start time (and, when the workout's distance is known, a close distance) — the same read-back-rather-than-trust-the-write posture already used for gear correction — and SHALL record that recovered id as the migrated activity rather than marking the workout failed. If no single activity satisfies both checks, the migration SHALL fall back to recording the workout as failed rather than guessing.

#### Scenario: Tracked workout uploads and lands in the ledger
- **WHEN** the migration processes a tracked workout absent from the ledger
- **THEN** the TCX is uploaded with the deterministic external_id and the created activity id is persisted in the ledger

#### Scenario: Re-run skips migrated workouts
- **WHEN** the migration runs again over the same archive
- **THEN** workouts present in the ledger are skipped without any upload call

#### Scenario: A duplicate rejection is reconciled by reading the activity back from Strava
- **WHEN** Strava rejects an upload as a duplicate of an existing activity and the rejection response carries no `activity_id`
- **THEN** the migration looks up the athlete's activities around the workout's start time, and if exactly one activity matches both the start time and (when the workout's distance is known) the distance, records that activity's id in the ledger as the migrated activity, the same as a successful upload

#### Scenario: An unconfirmed duplicate rejection still fails rather than guessing
- **WHEN** Strava rejects an upload as a duplicate without an `activity_id`, and no single activity in the athlete's activity list matches both the start time and distance
- **THEN** the workout is recorded as failed in the ledger, the same as any other genuine upload failure
