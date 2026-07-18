# workout-json-parsing

## MODIFIED Requirements

### Requirement: Workout scalar fields are parsed from the export list structure
Given an Endomondo workout export (a JSON array of single-key objects), the parser SHALL populate all workout-level fields present in the document: `name`, `sport`, `source`, `created_date`, `start_time`, `end_time`, `duration_s`, `distance_km`, `calories_kcal`, `altitude_min_m`, `altitude_max_m`, `speed_avg_kmh`, `speed_max_kmh`, `hydration_l`, `ascend_m`, `descend_m`. Numeric fields SHALL accept both integer and decimal JSON representations (`calories_kcal` and `speed_kmh` are decimal in the model, matching real files). Unknown keys SHALL be ignored.

#### Scenario: Manual workout header fields from the real fixture
- **WHEN** `fixtures/workout-manual.json` is parsed
- **THEN** name is "Sample manual walk", sport is "WALKING", source is "INPUT_MANUAL", duration_s is 7200, distance_km is 6.6, calories_kcal is 491.268, speed_avg_kmh is 3.3

#### Scenario: Tracked workout header fields from the real fixture
- **WHEN** `fixtures/workout-tracked.json` is parsed
- **THEN** all header fields are populated, including altitude_min_m 236.0, altitude_max_m 338.0, ascend_m 247.0, descend_m 286.0 despite their integer JSON representation

### Requirement: Numeric type mismatches are tolerated
The parser SHALL convert JSON numbers to the model's numeric type regardless of whether the JSON representation is integer or decimal. Parsing a real export file SHALL NOT throw for any numeric field.

#### Scenario: Decimal calories parse
- **WHEN** `fixtures/workout-manual.json` (calories_kcal 491.268) is parsed
- **THEN** the parse succeeds and calories_kcal is 491.268

#### Scenario: Integer altitudes parse
- **WHEN** `fixtures/workout-tracked.json` (altitude_min_m 236) is parsed
- **THEN** the parse succeeds and altitude_min_m is 236.0

### Requirement: Parsed points are attached to the result with all fields populated
The parser SHALL attach every entry of the `points` array to the returned workout, populating each point's `location` (latitude AND longitude), `altitude`, `distance_km`, `speed_kmh`, and `timestamp` from all of the point's single-key maps — not only the first one. Fields absent from a point SHALL remain null.

#### Scenario: Manual fixture points carry full locations
- **WHEN** `fixtures/workout-manual.json` (26 location-only points) is parsed
- **THEN** the result has 26 points, each with non-null latitude and longitude and null altitude/distance_km/speed_kmh/timestamp

#### Scenario: Tracked fixture points carry metrics
- **WHEN** `fixtures/workout-tracked.json` (8 points with altitude, distance, speed, timestamp) is parsed
- **THEN** the result has 8 points; the second point has altitude 302.0, distance_km 0.0, speed_kmh 0.0, a non-null timestamp, and a location with both coordinates

### Requirement: Malformed input raises a domain exception the upload flow handles
The parser SHALL throw `InvalidWorkoutJsonException` (wrapping the underlying cause) for input that is not syntactically valid JSON AND for valid JSON that is not an array of objects. The upload flow SHALL catch this exception and render the user-facing invalid-format error message for both cases.

#### Scenario: Non-JSON upload
- **WHEN** a file containing `this is not json` is parsed
- **THEN** an `InvalidWorkoutJsonException` propagates out of the parser

#### Scenario: Valid JSON of the wrong shape
- **WHEN** a file containing a JSON object (`{}`) instead of an array is parsed
- **THEN** an `InvalidWorkoutJsonException` propagates out of the parser

#### Scenario: Upload flow renders the error for wrong-shape input
- **WHEN** a wrong-shape JSON file is submitted to `/upload/process`
- **THEN** the upload view renders with the invalid-format error message instead of the error page
