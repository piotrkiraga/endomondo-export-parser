## Context

The parser is ~160 lines of hand-rolled map-walking inside `UploadController`, with defects pinned by characterization tests: `ClassCastException` on decimal calories, points discarded, only the first single-key map of each point/location examined, per-point fields read from the wrong map. The export format is frozen (Endomondo is gone): a JSON array of single-key objects; `points` is an array of arrays of single-key maps; `location` is an array of arrays of single-key maps holding `latitude`/`longitude`. The parser currently runs on a pinned Jackson 2 while the rest of Boot 4.1 uses Jackson 3.

## Goals / Non-Goals

**Goals:**
- Real export files parse end-to-end, fully populated; defects fixed by construction, not by patching the loop.
- Parser isolated in a service with a domain exception boundary; controller only orchestrates.
- Jackson 3 migration completed; Jackson 2 pin removed.
- Test suite flips from characterization (what it did) to regression (what it must do), in one reviewable diff.

**Non-Goals:**
- TCX/GPX handling, persistence, UI changes, DTO renaming (snake_case mirrors the frozen format).

## Decisions

- **Merge-then-bind instead of custom deserializers or a patched loop** — read the document as a tree, merge each array-of-single-key-objects into one object node (recursively for `points` and `location`), then bind the merged node to the DTOs with `ObjectMapper`. One small generic helper handles all three nesting levels; the DTOs stay dumb. Alternatives rejected: patching the existing loop keeps the shape that caused every defect; per-class custom deserializers are three times the code for the same result.
- **`InvalidWorkoutJsonException` as the parser's only failure mode** — unchecked, wraps the Jackson cause. The controller catches the domain type and stays decoupled from Jackson 3's exception hierarchy (which differs from Jackson 2's: unchecked, `JsonParseException` → `StreamReadException`). Wrong-shape input becomes a handled error instead of a 500 — a deliberate, spec'd behavior change.
- **Model types follow the file format**: `calories_kcal` and `Point.speed_kmh` become `Double`. The format is frozen, so "what real files contain" is the final word. Kept as Lombok POJOs rather than records — `Gpx`/`Metadata` siblings share the style, and the merge-then-bind approach works with either.
- **Jackson 3 now, in the same change** — the parser rewrite already touches every Jackson call site and every exception assertion, so folding the migration in costs nearly nothing extra, while a separate later change would re-touch all of it. Verify Jackson 3 defaults at apply time (notably: unknown-property failure default differs from Jackson 2; the spec requires unknown keys ignored either way).
- **Characterization tests updated in place, not replaced** — same test class, assertions flipped where defects were pinned; "(pinned defect)" comments removed. The single diff is the review artifact showing exactly what changed behaviorally. Fixture golden tests added for full-document assertions; one `@SpringBootTest` + MockMvc test covers the upload flow's info/error messages.

## Risks / Trade-offs

- [Jackson 3 API knowledge partially post-cutoff] → consult Jackson 3 migration notes at apply time; compile errors and the existing malformed-input tests are the map.
- [Merged node silently drops duplicate keys if a file repeats one] → last-wins merge, documented in the helper; frozen format makes repeats unlikely, and golden tests over real-structure fixtures would surface it.
- [`@SpringBootTest` web test entangles security/CSRF] → the permitAll list already covers `/upload/process`; if CSRF blocks the POST, the test posts with the token as a browser would — behavior, not configuration, is what's under test.

## Migration Plan

Single change: service + tests land together; `mvnw clean verify` green; manual upload of a real file from `data/` as the end-to-end check. Rollback = revert the commit (spec deltas revert with it).
