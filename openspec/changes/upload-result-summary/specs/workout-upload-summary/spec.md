# workout-upload-summary

## ADDED Requirements

### Requirement: Successful upload renders a workout summary
After a workout JSON file is processed successfully, the upload view SHALL render, in addition to the existing success message, a summary of the parsed workout containing: name, sport, source, start time, duration, distance, calories, average and maximum speed, altitude range (minimum and maximum), the number of GPS points, the number of points carrying a full location (latitude and longitude), and — when the workout model carries pictures — the number of pictures. The summary SHALL NOT appear when processing failed or no file was processed.

#### Scenario: Tracked workout shows full summary
- **WHEN** `fixtures/workout-tracked.json` is uploaded via the upload form
- **THEN** the response contains the existing success message and a summary with the fixture's sport, source, start time, duration, distance, calories, and its point counts

#### Scenario: No summary on failure
- **WHEN** a non-JSON file is uploaded
- **THEN** the response contains the existing invalid-JSON error message and no summary block

### Requirement: Absent values are shown as absent
Summary fields whose values are missing from the uploaded file SHALL render a localized "not present in file" marker, and SHALL NOT render as empty, zero, or omitted rows.

#### Scenario: Manual workout marks missing metrics as absent
- **WHEN** `fixtures/workout-manual.json` (no maximum speed, no altitude range) is uploaded
- **THEN** the summary renders the absent-value marker for those fields while still showing the values the file does contain (e.g. distance, calories, point count)

### Requirement: Summary labels are localized
All summary labels and the absent-value marker SHALL come from the message bundles with English and Polish entries — no hardcoded display text in the template.

#### Scenario: Labels resolve from message bundles
- **WHEN** the summary is rendered with the English locale
- **THEN** every label text originates from a `summary.*` key present in both `messages.properties` and `messages_pl.properties`
