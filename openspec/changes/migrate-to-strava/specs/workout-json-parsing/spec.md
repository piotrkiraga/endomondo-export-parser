# workout-json-parsing

## ADDED Requirements

### Requirement: Picture entries are parsed into the workout model
The parser SHALL populate the workout's pictures from the `pictures` entry — for each picture: `created_date` and the archive-relative `url` (nested under `picture`) — using the same single-key-map normalization as points. A picture's own `point` (coordinates, present on a minority of pictures) SHALL be tolerated and ignored rather than modelled: a picture belongs to its workout by file containment, not by location, and photos are never transferred to Strava. Workouts without a `pictures` entry SHALL have an empty pictures list.

#### Scenario: Picture-bearing workout parses its pictures
- **WHEN** a workout JSON containing a `pictures` entry (created_date, nested picture url) is parsed
- **THEN** the result carries one picture per entry with that created_date and the archive-relative url

#### Scenario: A picture's coordinates are ignored, not a parse failure
- **WHEN** a picture entry additionally carries a `point` with latitude and longitude
- **THEN** the workout parses successfully and the picture exposes only its created_date and url

#### Scenario: Workouts without pictures stay empty
- **WHEN** `fixtures/workout-manual.json` (no pictures entry) is parsed
- **THEN** the result's pictures list is empty
