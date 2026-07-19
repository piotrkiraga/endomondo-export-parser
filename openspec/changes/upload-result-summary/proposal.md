## Why

Since the parser fix, a successful upload parses the full workout into `EndomondoJson` — and then discards it: the user sees only the one-line message "Submitted file processed successfully", with no indication of what was actually recognized. The data for a real summary (sport, source, dates, metrics, points, pictures) is already in memory at that moment; it is simply never shown.

## What Changes

- After successful processing, the upload view renders a workout summary from the parsed model: sport, source, start time, duration, distance, calories, GPS point count (and how many carry full location), picture count.
- Fields absent from the uploaded file (e.g. manual workouts have no per-point metrics) are shown as absent, not as blank/zero pretending to be data.
- All new view text is externalized to the message bundles (English and Polish) — no hardcoded labels.
- The failure path is unchanged: invalid JSON still produces the existing error message and no summary.

**Preserved behavior (pinned by the existing upload-flow regression tests):** parse semantics, the empty-file validation error, the invalid-JSON error message, and the success message itself (the summary appears in addition to it).

## Non-goals

- Fixing the ISO-8859-1 mojibake in Polish messages or translating existing hardcoded template text — that is the separate `fix-localization` change (the new labels this change adds are properly externalized from the start, so they benefit automatically once encoding is fixed).
- Persisting uploads, multi-file upload, TCX uploads, or any styling/layout overhaul beyond the summary block.
- Strava migration concerns (separate `migrate-to-strava` change).

## Capabilities

### New Capabilities
- `workout-upload-summary`: what the upload view presents after processing a workout file — the summary content, absent-field handling, and localization of its labels.

### Modified Capabilities

(none — `workout-json-parsing` requirements are unchanged; this change only presents what the parser already produces)

## Impact

- `UploadController.process()` — passes the parsed `EndomondoJson` (or a small view model derived from it) to the view instead of dropping it.
- `upload.html` — new summary section rendered only when a workout was processed.
- `messages.properties` / `messages_pl.properties` — new label keys.
- `UploadFlowTest` — extended to assert summary content for the manual and tracked fixtures.
