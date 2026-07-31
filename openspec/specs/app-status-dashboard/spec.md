# app-status-dashboard Specification

## Purpose
TBD - created by archiving change enriching-strava-data-view. Update Purpose after archive.
## Requirements
### Requirement: The home page shows the archive's total workout count
The home page SHALL show the total number of workouts found in the configured archive, computed by scanning the archive the same way the migration planner does, without parsing each workout's content.

#### Scenario: Archive is configured and present
- **WHEN** the home page is requested and the configured archive directory exists
- **THEN** the page shows the total count of JSON-paired workouts found in it

#### Scenario: Archive is not configured or missing
- **WHEN** the home page is requested and the configured archive directory does not exist
- **THEN** the page shows the archive count as unavailable rather than an error

### Requirement: The home page shows the migration ledger's status breakdown
The home page SHALL show, from the migration ledger, a count of workouts in each status: done, failed, skipped, and pending.

#### Scenario: Ledger has entries in multiple states
- **WHEN** the home page is requested and the ledger contains entries with different statuses
- **THEN** the page shows a separate count for each of done, failed, skipped, and pending

#### Scenario: Ledger is empty
- **WHEN** the home page is requested and no migration has ever run
- **THEN** all four ledger counts show as zero, not an error

### Requirement: The home page shows the location cache's size
The home page SHALL show the number of distinct coordinates the location cache currently holds.

#### Scenario: Cache has cached lookups
- **WHEN** the home page is requested and the location cache file contains entries
- **THEN** the page shows their count

#### Scenario: Cache is empty or absent
- **WHEN** the home page is requested and the location cache has never been written
- **THEN** the page shows the count as zero, not an error

### Requirement: The home page shows the Strava athlete profile and gear dictionary
The home page SHALL show the connected Strava athlete's profile (name, picture, location when available) and the full gear dictionary (every bike/shoe name and id), sourced from the Strava dictionary cache — replacing the separate Strava Dictionary page, which is retired.

#### Scenario: Dictionary has been refreshed
- **WHEN** the home page is requested and the dictionary cache holds a snapshot
- **THEN** the page shows the athlete's profile and the full gear table from that snapshot

#### Scenario: Dictionary has never been refreshed
- **WHEN** the home page is requested and the dictionary cache is empty
- **THEN** the page shows the dictionary section as "not yet refreshed" rather than an error

#### Scenario: The Strava Dictionary page no longer exists as a separate destination
- **WHEN** the application is running after this change
- **THEN** the athlete/gear dictionary is only reachable from the home page; no separate `/migration/strava-dictionary` page or navbar entry exists

### Requirement: The home page can refresh the Strava dictionary on demand
The home page SHALL offer the same manual "refresh from Strava" action the retired dictionary page offered, making the same one `GET /athlete` call and updating the same underlying dictionary cache.

#### Scenario: Refresh succeeds
- **WHEN** the user submits the refresh action from the home page while connected to Strava
- **THEN** the dictionary cache is updated and the home page reflects the refreshed athlete/gear data

#### Scenario: Refresh fails
- **WHEN** the refresh action fails (e.g. not connected, or a Strava API error)
- **THEN** the home page shows an error message and leaves the previously cached dictionary data, if any, unchanged

### Requirement: The dashboard makes no Strava API calls beyond the existing refresh action
Computing every dashboard stat (archive count, ledger breakdown, location cache size, dictionary display) SHALL read only local files — none of it SHALL trigger a live Strava API request. The one exception, unchanged from today, is the explicit refresh action itself.

#### Scenario: Dashboard renders while disconnected from Strava
- **WHEN** the home page is requested and no Strava token is stored
- **THEN** every dashboard stat still renders from local data, and no Strava API call is made

