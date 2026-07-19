## Context

`UploadController.process()` already receives the fully bound `EndomondoJson` from `EndomondoJsonParser` and logs it at debug level; the view gets only flat message lists. The model (Lombok POJO, snake_case fields) carries: `sport`, `source`, `start_time`/`end_time`, `duration_s`, `distance_km`, `calories_kcal`, `speed_avg_kmh`/`speed_max_kmh`, `altitude_min_m`/`altitude_max_m`, `points` (each with optional location and per-point metrics), and — once the `migrate-to-strava` change lands its parser task — `pictures`. Manual workouts (`INPUT_MANUAL`) have location-only points and null metrics; tracked workouts have full points. The observed fixtures cover both shapes.

## Goals / Non-Goals

**Goals:** show the user what was recognized in the uploaded file, distinguishing "value present" from "value absent", localized from day one.

**Non-Goals:** changing parse semantics; fixing the pre-existing message-encoding defect (separate `fix-localization` change); persisting anything server-side.

## Decisions

- **Dedicated view model (`WorkoutSummary`) built in the controller, not raw `EndomondoJson` in the template** — the template needs derived values (point count, points-with-location count, formatted duration) and null-safe display logic; computing them in a small immutable view model keeps Thymeleaf expressions trivial and testable. Alternative rejected: passing `EndomondoJson` directly (template becomes a thicket of `th:if` null checks and SpEL arithmetic).
- **Absent values render as a localized "not present in file" marker, not empty cells** — the whole point of the summary is telling the user what their file contained. Silently blank cells are indistinguishable from rendering bugs.
- **Summary appears alongside the existing success message, conditionally on the model attribute** — the success/error message contract is pinned by regression tests and stays untouched; the summary block is additive (`th:if="${summary != null}"`).
- **Labels via new `summary.*` message keys in both bundles** — consistent with the existing `title.*`/`infoMessage.*` naming. Polish values will render mojibake until `fix-localization` lands; that is an accepted, already-tracked defect of the message infrastructure, not of this change.
- **Duration formatted as `H:mm:ss` from `duration_s`; distances/calories shown with the units already encoded in the field names (km, kcal)** — no unit conversion logic; the export's units are the display units.

## Risks / Trade-offs

- [Sequencing with `migrate-to-strava`'s pictures parsing] → the summary shows a picture count only if the model has a `pictures` field at implementation time; otherwise the row is omitted and a note is left in tasks.md. Whichever change lands second reconciles.
- [Locale-dependent number formatting (comma vs dot decimals)] → format numeric values explicitly in the view model (fixed `Locale.ROOT` with one decimal place) so tests assert stable strings in both languages.

## Migration Plan

Pure additive view change; rollback = revert. No data, no config, no external calls.

## Open Questions

- None.
