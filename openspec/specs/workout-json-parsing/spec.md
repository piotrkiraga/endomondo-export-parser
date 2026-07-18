# workout-json-parsing Specification

## Purpose
TBD - created by archiving change add-characterization-tests. Update Purpose after archive.
## Requirements
### Requirement: Workout scalar fields are parsed from the export list structure
Given an Endomondo workout export (a JSON array of single-key objects), the parser SHALL populate the workout-level fields whose JSON value type matches the model field type: `name`, `sport`, `source`, `created_date`, `start_time`, `end_time`, `duration_s` (integer), `distance_km` (decimal), `speed_avg_kmh` (decimal).

#### Scenario: Type-conforming workout header fields
- **WHEN** a workout JSON is parsed whose numeric values all match the model types (integer `duration_s` and `calories_kcal`, decimal `distance_km` and `speed_avg_kmh`)
- **THEN** all scalar fields are populated with the corresponding values

(Note: field population is NOT observable when parsing the real fixtures — the type-cast crash below discards the partially populated result.)

### Requirement: Parsing crashes on numeric values whose JSON type mismatches the model type (pinned defect)
The parser blindly casts JSON numbers to the model's boxed type. It SHALL throw `ClassCastException` when a field's JSON representation differs in kind from the model type — notably decimal `calories_kcal` values (model type `Integer`), which occur in real export files, and integer altitude/ascend/descend values (model type `Double`).

#### Scenario: Decimal calories crash the parse
- **WHEN** `fixtures/workout-manual.json` (calories_kcal 491.268) is parsed
- **THEN** a `ClassCastException` propagates out of the parser

#### Scenario: Tracked workout crashes the same way
- **WHEN** `fixtures/workout-tracked.json` (calories_kcal 1339.81) is parsed
- **THEN** a `ClassCastException` propagates out of the parser

### Requirement: Parsed points are never attached to the result (pinned defect)
The parser builds `Point` objects from the `points` entry but SHALL leave the returned workout's points as the default empty list, because the result is only assigned on unrecognized keys.

#### Scenario: Points list is empty after parsing a points-bearing document
- **WHEN** a workout JSON containing a `points` entry (and no entries that crash the parse) is parsed
- **THEN** the returned workout's points list is empty

### Requirement: Malformed input raises a Jackson parse error
The parser SHALL propagate `JsonParseException` for input that is not syntactically valid JSON; the upload flow relies on this exception type to render a user-facing error message.

#### Scenario: Non-JSON upload
- **WHEN** a file containing `this is not json` is parsed
- **THEN** a `JsonParseException` propagates out of the parser

#### Scenario: Valid JSON of the wrong shape is not handled as a parse error
- **WHEN** a file containing a JSON object (`{}`) instead of an array is parsed
- **THEN** a Jackson mapping exception that is not a `JsonParseException` propagates (the upload flow does not catch it)

