# strava-migration

## ADDED Requirements

### Requirement: Tracked workouts upload as TCX with idempotent external ids
For workouts whose source is a GPS track (not `INPUT_MANUAL`), the migration SHALL upload the paired TCX file via the Strava upload API with a deterministic `external_id` derived from the workout filename, poll the upload until processed, and record the resulting activity id in the ledger. Re-running the migration SHALL NOT create duplicates for workouts already in the ledger or rejected by Strava as duplicates.

#### Scenario: Tracked workout uploads and lands in the ledger
- **WHEN** the migration processes a tracked workout absent from the ledger
- **THEN** the TCX is uploaded with the deterministic external_id and the created activity id is persisted in the ledger

#### Scenario: Re-run skips migrated workouts
- **WHEN** the migration runs again over the same archive
- **THEN** workouts present in the ledger are skipped without any upload call

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
The migration SHALL throttle below Strava's published rate limits, back off on 429 responses, and on interruption resume from the ledger without repeating completed work.

#### Scenario: Rate-limit response
- **WHEN** the API returns 429
- **THEN** the migration waits for the limit window before continuing, without marking the workout failed

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
