## MODIFIED Requirements

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

### Requirement: The migration respects Strava rate limits and resumes
The migration SHALL throttle below Strava's published rate limits, back off on 429 responses, and on interruption resume from the ledger without repeating completed work. When a 429 response triggers a wait for the rate-limit window to reset, that wait SHALL be logged with its expected duration at the moment it begins, so a long wait is distinguishable from an application hang.

#### Scenario: Rate-limit response
- **WHEN** the API returns 429
- **THEN** the migration waits for the limit window before continuing, without marking the workout failed

#### Scenario: A rate-limit wait is logged, not silent
- **WHEN** a 429 response triggers a wait for the rate-limit window to reset
- **THEN** a log entry is written at the start of the wait stating how long it is expected to last
