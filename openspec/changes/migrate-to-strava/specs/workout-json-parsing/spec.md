# workout-json-parsing

## ADDED Requirements

### Requirement: Picture entries are parsed into the workout model
The parser SHALL populate the workout's pictures from the `pictures` entry — for each picture: `created_date`, the relative `url` (nested under `picture`), and the GPS `point` where present — using the same single-key-map normalization as points. Workouts without a `pictures` entry SHALL have an empty pictures list.

#### Scenario: Picture-bearing workout parses its pictures
- **WHEN** a workout JSON containing a `pictures` entry (created_date, nested picture url, point) is parsed
- **THEN** the result carries one picture with that created_date, the relative url, and the point's coordinates

#### Scenario: Workouts without pictures stay empty
- **WHEN** `fixtures/workout-manual.json` (no pictures entry) is parsed
- **THEN** the result's pictures list is empty
