## Why

The parser has never successfully parsed a real Endomondo export file: it crashes on decimal calorie values, silently discards every GPS point it builds, and never populates longitude or per-point metrics. All of this is pinned by characterization tests and spec'd as "(pinned defect)" requirements. With the platform stable on Boot 4.1, this change replaces the hand-rolled map-walking with proper Jackson data binding and deliberately flips the pinned defects to intended behavior.

## What Changes

- Extract parsing out of `UploadController` into a dedicated service that merges the export's single-key-map structure and binds it to the DTOs.
- Fix the pinned defects: tolerant numeric conversion (decimal calories, integer altitudes), points attached to the result, all point fields populated (altitude, distance, speed, timestamp), longitude populated.
- Model type corrections to match the frozen file format: `calories_kcal` `Integer` → `Double` (real files carry decimals), `Point.speed_kmh` `Integer` → `Double` (real files carry decimals like 3.32816).
- Migrate the parser to Jackson 3 (`tools.jackson`) and drop the Jackson 2 `jackson-databind` pin from the pom.
- Introduce a domain exception (`InvalidWorkoutJsonException`) wrapping all malformed-input causes; the upload flow catches it and renders the user-facing error for **both** non-JSON input and valid-JSON-wrong-shape input (previously unhandled).
- **BREAKING** (to the pinned-defect contract, on purpose): characterization tests are updated to assert intended behavior and become the regression suite; fixture-based golden tests are added; a web-layer test covers the upload flow's success and error messages.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities
- `workout-json-parsing`: all four requirements are MODIFIED — scalar parsing becomes type-tolerant, the two "(pinned defect)" requirements flip to intended behavior (crash → tolerant parse; lost points → fully populated points), and malformed-input handling becomes a domain exception covering both malformed and wrong-shape input.
- `build-platform`: the Jackson 2 pin exception is removed from the no-pins requirement — the parser moves to Boot-managed Jackson 3.

## Non-goals

- No TCX/GPX parsing or conversion features (that is the re-scoping conversation, next).
- No UI changes beyond the existing error/info message flow.
- No persistence of parsed workouts.
- No renaming of the snake_case DTO fields — they mirror the frozen file format.

## Preserved vs. changed behavior

- **Preserved**: upload flow UX (same views, same message keys), security, all `export-data-management` and remaining `build-platform` requirements. Well-formed scalar parsing keeps identical results.
- **Changed (deliberately)**: real export files now parse successfully end-to-end; wrong-shape JSON now yields the user-facing error instead of a 500; the pinned-defect assertions are rewritten — each flip is traceable in the test diff.

## Impact

- New parser service + exception class; `UploadController` shrinks to flow handling.
- `EndomondoJson`/`Point` field type corrections; `pom.xml` loses the Jackson 2 dependency.
- Test suite: characterization tests updated in place (the diff documents old vs. new behavior), plus fixture golden tests and one web-layer flow test.
- Verification: `mvnw clean verify` green; manual upload of a real workout file from `data/` succeeds in the running app.
