# migration-photo-report

## ADDED Requirements

### Requirement: Every archived photo is accounted for exactly once
The report generator SHALL include every photo referenced by workout JSON `pictures` entries under its owning activity, and SHALL list photos present under `resources/gfx/` but referenced by no workout in an explicit "unmatched" section. No photo appears twice.

#### Scenario: Referenced photos grouped by activity
- **WHEN** the report is generated after (or in dry-run before) migration
- **THEN** each of the 29 picture-bearing workouts shows its photos grouped under that workout's entry

#### Scenario: Unmatched photos are surfaced, not dropped
- **WHEN** a photo file exists in the archive with no referencing workout
- **THEN** it appears in the unmatched section with its file path

### Requirement: The report enables one-step manual photo attachment
For each migrated activity with photos, the report SHALL show the photos (rendered from their local archive paths) directly beside a hyperlink to the created Strava activity (`https://www.strava.com/activities/{id}`), plus the workout name and date. Before migration (dry-run), the activity link column SHALL show "pending migration".

#### Scenario: Post-migration report links photos to activities
- **WHEN** the report is generated after a migration run
- **THEN** each photo row pairs the image with the clickable Strava activity link for its workout

### Requirement: The report is a self-contained local file
The report SHALL be written as a single HTML file under `data/` (git-ignored, since photos are personal data), openable directly in a browser with images loading from the local archive.

#### Scenario: Report opens offline
- **WHEN** the generated HTML file is opened from disk
- **THEN** photos render and Strava links are present without the application running
