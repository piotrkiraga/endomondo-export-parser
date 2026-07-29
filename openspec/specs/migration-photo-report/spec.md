# migration-photo-report Specification

## Purpose
Defines the standalone HTML report that hands off photos Strava's API cannot accept: accounting for every archived photo exactly once, pairing photos with links to their migrated Strava activities, geotagging handed-out photo copies via EXIF, and presenting a self-contained, offline-viewable local file.

## Requirements
### Requirement: Every archived photo is accounted for exactly once
The report generator SHALL include every photo referenced by workout JSON `pictures` entries under its owning activity, and SHALL list photos present under `resources/gfx/` but referenced by no workout in an explicit "unmatched" section. No photo appears twice.

#### Scenario: Referenced photos grouped by activity
- **WHEN** the report is generated after (or in dry-run before) migration
- **THEN** each of the 29 picture-bearing workouts shows its photos grouped under that workout's entry

#### Scenario: Unmatched photos are surfaced, not dropped
- **WHEN** a photo file exists in the archive with no referencing workout
- **THEN** it appears in the unmatched section with its file path

### Requirement: The report enables one-step manual photo attachment
For each migrated activity with photos, the report SHALL show the photos (rendered from their local archive paths) directly beside a hyperlink to the created Strava activity (`https://www.strava.com/activities/{id}`), which SHALL open in a new browser tab so the report itself remains open at the user's place in the list, plus the workout name and date. Before migration (dry-run), the activity link column SHALL show "pending migration".

#### Scenario: Post-migration report links photos to activities
- **WHEN** the report is generated after a migration run
- **THEN** each photo row pairs the image with the clickable Strava activity link for its workout

#### Scenario: Activity link opens without navigating away from the report
- **WHEN** the user clicks a "view on Strava" link
- **THEN** the Strava activity page opens in a new browser tab and the report remains open in its original tab, unnavigated

### Requirement: Photos are handed out with their location stamped into EXIF
Because Strava's API cannot accept photos, the photos a user attaches manually SHALL carry their own geotag. For each referenced photo the generator SHALL resolve a location from three sources, in this order: the photo file's existing EXIF GPS tags; the picture's `point` in the workout JSON; the first track point of the owning workout. It SHALL then write the resolved coordinates (and, absent one, the picture's `created_date` as the EXIF timestamp) into a **copy** of the photo written under `data/`. The archive's own files SHALL never be modified.

Resolving EXIF first makes re-generation idempotent: a copy stamped by an earlier run is read back and left unchanged.

#### Scenario: Picture with JSON coordinates is stamped from them
- **WHEN** a photo without EXIF GPS belongs to a picture entry carrying a `point`
- **THEN** the handed-out copy carries EXIF GPS coordinates equal to that point

#### Scenario: Picture without coordinates falls back to the workout track
- **WHEN** a photo has neither EXIF GPS nor a picture `point`, and its workout has track points
- **THEN** the handed-out copy is stamped with the workout's first track point

#### Scenario: Existing EXIF wins and regeneration is stable
- **WHEN** the report is generated twice over the same archive
- **THEN** the second run reads the EXIF written by the first and leaves those coordinates unchanged

#### Scenario: A workout with no location at all is reported, not guessed
- **WHEN** a photo has no EXIF GPS, no picture `point`, and its workout has no track points
- **THEN** the copy is handed out ungeotagged and the report marks it as having no available location

### Requirement: Photos can be viewed at full size and are captioned with their approximate place
Clicking a photo thumbnail in the report SHALL display it at full size without navigating away from the report. When a photo's resolved location reverse-geocodes to a place (city/suburb, optionally with a notable nearby feature), the report SHALL show that place as the photo's caption instead of the raw location-source label; when no place is available, the caption SHALL fall back to indicating the location's source or absence, as before.

#### Scenario: Thumbnail opens full size in place
- **WHEN** the user clicks a photo thumbnail
- **THEN** the full-resolution photo is displayed in an overlay on the same page, closable without leaving the report

#### Scenario: Caption shows the resolved place
- **WHEN** a photo's coordinates reverse-geocode to "Kraków" with a nearby feature "Wawel Castle"
- **THEN** the photo's caption reads a description including "Wawel Castle" and "Kraków"

### Requirement: The report is a self-contained local file
The report SHALL be written as a single HTML file under `data/` (git-ignored, since photos are personal data), openable directly in a browser with images loading from the local archive.

#### Scenario: Report opens offline
- **WHEN** the generated HTML file is opened from disk
- **THEN** photos render and Strava links are present without the application running
