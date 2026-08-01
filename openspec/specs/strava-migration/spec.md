# strava-migration Specification

## Purpose
Defines how workouts from an Endomondo export are migrated to Strava: uploading tracked workouts as TCX files versus creating manual activities, applying names/sport types/descriptions, best-effort gear correction, location-based name enrichment, rate-limit handling and resumability, dry-run planning, and the requirement that migration only ever runs from an explicit user action.
## Requirements
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

### Requirement: Manual workouts become manual Strava activities
For `INPUT_MANUAL` workouts, the migration SHALL create a manual activity (name, mapped sport, start time, elapsed duration, distance) instead of uploading their TCX, because the export's manual TCX trackpoints are synthetic and would draw false routes.

#### Scenario: Manual workout created without a track
- **WHEN** the migration processes an INPUT_MANUAL workout
- **THEN** a manual activity is created from the JSON metadata and no TCX upload occurs for it

### Requirement: Metadata transfers from the workout JSON
After an activity exists, the migration SHALL set its name from the JSON `name` — or, when that is blank, from a generated "{time of day} {sport}" name derived from the workout's local start hour and mapped Strava sport type — and its sport type via a total mapping from every Endomondo sport value present in the archive to a Strava sport type, and SHALL mark the activity description with a migration note including the original workout date.

#### Scenario: Name and sport applied
- **WHEN** a workout titled "Sample tracked ride" with sport CYCLING_SPORT is migrated
- **THEN** the Strava activity is renamed "Sample tracked ride" with sport type Ride

#### Scenario: Unnamed workout gets a generated name
- **WHEN** a workout with no JSON `name`, sport RUNNING, and a start time of 07:15 is migrated
- **THEN** the Strava activity is named "Morning Run"

#### Scenario: Unmapped sport halts that workout, not the run
- **WHEN** a workout carries a sport value missing from the mapping
- **THEN** the workout is reported as skipped-with-reason and the migration continues

#### Scenario: A duplicate rejection without a returned activity id is recorded as failed, not silently reconciled
- **WHEN** Strava rejects an upload as a duplicate of an existing activity but the rejection response carries no `activity_id`
- **THEN** the workout is recorded as failed in the ledger, even though no duplicate activity was actually created, because success is decided solely by an activity id being present in the response

### Requirement: A configured old-bike gear correction is attempted best-effort, and shown as confirmed rather than assumed
For a Ride workout dated on or before a configured cutoff date, with an old bike's gear id configured, the migration SHALL include that gear id on the activity's post-upload metadata update. This correction is best-effort only: Strava's public API is known to silently accept and discard `gear_id` on some activities rather than applying it (a real, undocumented platform limitation, not a defect in this app's request). Because the write cannot be trusted, any page displaying a migrated workout's gear (the review page, the workout report) SHALL read the activity's actual gear back from Strava when connected, and label it as confirmed; only when not connected, or for a not-yet-migrated workout, SHALL the offline-computed planned gear id be shown, explicitly labeled as planned rather than confirmed. The review page's confirmed-gear read SHALL always be a live Strava read. The workout report's confirmed-gear read MAY instead be satisfied from a previously confirmed value for that activity, without a new Strava call, as long as that value was itself obtained from a live read at some point.

#### Scenario: Gear correction is attempted for a pre-cutoff Ride
- **WHEN** a Ride workout dated on or before the configured cutoff date is migrated with an old-bike gear id configured
- **THEN** the post-upload metadata update includes that gear id

#### Scenario: Gear correction does not apply outside its configured scope
- **WHEN** a workout is not a Ride, is dated after the configured cutoff, or no old-bike gear id is configured
- **THEN** the metadata update omits `gear_id` entirely, never sending it as `null`

#### Scenario: Confirmed gear, not the planned value, is shown once connected
- **WHEN** the review page or workout report displays a migrated workout's gear while connected to Strava
- **THEN** it shows the gear actually read back from the activity, labeled as confirmed, even if it differs from the planned gear id

#### Scenario: The review page always reads live, even if a cached value exists
- **WHEN** the review page displays a migrated workout's confirmed gear
- **THEN** it performs a live Strava read rather than reusing a previously cached value, so a gear correction made directly on Strava is visible immediately

#### Scenario: The workout report reuses a previously confirmed value
- **WHEN** the workout report displays a migrated workout's confirmed gear and that activity's gear was already successfully confirmed on an earlier occasion
- **THEN** it shows that previously confirmed value without making a new Strava call for it

### Requirement: Generated names and descriptions are enriched with the workout's approximate location
When a workout's name is generated (not JSON-supplied) and its starting coordinates resolve to a notable nearby feature (a river/water body, a historic site or landmark, a park, or a named boulevard) via reverse geocoding, the migration SHALL append that place to the generated name, and SHALL append a corresponding sentence to the activity description. Enrichment SHALL never modify a JSON-supplied name. A geocoding failure or an absent nearby feature SHALL NOT block the workout; the name and description fall back to their un-enriched form.

#### Scenario: Generated name gains a place
- **WHEN** an unnamed evening ride starts near a river named "Vistula" in a city named "Kraków"
- **THEN** the generated name is "Evening Ride along Vistula in Kraków"

#### Scenario: JSON-supplied names are left alone
- **WHEN** a workout titled "Sample tracked ride" starts near a notable nearby feature
- **THEN** the migrated activity's name remains exactly "Sample tracked ride"

#### Scenario: Geocoding failure does not block migration
- **WHEN** the reverse-geocoding service is unreachable or returns no result for a workout's coordinates
- **THEN** the workout is still migrated, with its plain generated name and un-enriched description

### Requirement: The migration respects Strava rate limits and resumes
The migration SHALL throttle below Strava's published rate limits, back off on 429 responses, and on interruption resume from the ledger without repeating completed work. When a 429 response triggers a wait for the rate-limit window to reset, that wait SHALL be logged with its expected duration at the moment it begins, so a long wait is distinguishable from an application hang.

#### Scenario: Rate-limit response
- **WHEN** the API returns 429
- **THEN** the migration waits for the limit window before continuing, without marking the workout failed

#### Scenario: A rate-limit wait is logged, not silent
- **WHEN** a 429 response triggers a wait for the rate-limit window to reset
- **THEN** a log entry is written at the start of the wait stating how long it is expected to last

### Requirement: Dry-run produces the full plan without side effects
A dry-run SHALL enumerate every workout with its intended action (TCX upload / manual create / skip with reason), mapped sport, and photo count, performing zero Strava API calls.

#### Scenario: Dry-run is side-effect free
- **WHEN** a dry-run executes over the archive
- **THEN** the complete plan is produced and no HTTP request reaches the Strava API

### Requirement: Migration runs only on explicit user action
Migration (and OAuth) SHALL only start from an explicit user action on the migration page — never on application startup or on a schedule.

#### Scenario: Boot is inert
- **WHEN** the application starts
- **THEN** no Strava API call is made

### Requirement: The review page's resolved plan is cached across requests, not recomputed per request
The interactive migration review page SHALL reuse a previously resolved plan for the current archive across requests within the same running application instance, rather than re-scanning the archive and re-resolving every workout on each request. The cached plan SHALL be invalidated for a workout the moment a migrate or skip decision is recorded for it through the review page itself, so the page always reflects that decision without requiring an application restart.

#### Scenario: Revisiting the review page reuses the prior resolution
- **WHEN** the review page is requested again for the same archive within the same running application instance
- **THEN** the response reflects the previously resolved plan without re-scanning the archive from disk

#### Scenario: A decision made through the review page is reflected immediately
- **WHEN** the user migrates or skips a workout from the review page
- **THEN** the page's next view of that workout, and its own redirect logic, show the updated decision without needing an application restart

#### Scenario: A fresh application start resolves the plan again
- **WHEN** the application restarts
- **THEN** the next review-page request resolves the plan from the archive and ledger from scratch, exactly as it did before this change

