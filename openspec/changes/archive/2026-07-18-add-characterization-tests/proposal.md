## Why

The Endomondo JSON parser is about to be modernized (platform upgrade, then rewrite), but nothing records what it currently does — and code reading suggests it does surprising things (points never attached to the result, longitude never populated, a `ClassCastException` on decimal `calories_kcal` values that real export files contain). Characterization tests must pin the current behavior first, so every later change is diffed against a known baseline instead of guesswork. A minimal Lombok bump is included because the project currently cannot compile at all on the available JDK 17, and tests that cannot run pin nothing.

## What Changes

- Bump Lombok 1.18.12 → 1.18.30 — the smallest change that makes the project compile on JDK 17. No other dependency moves.
- Widen `UploadController.processEndomondoJson` from `private` to package-private as a test seam. No logic changes.
- Add JUnit 5 characterization tests that run the parser against the committed fixtures (`workout-manual.json`, `workout-tracked.json`) and malformed input, asserting the **observed** behavior — including defects — exactly as it is.
- Document each pinned defect in the spec so the future parser-fix change has explicit requirements to modify.

## Capabilities

### New Capabilities
- `workout-json-parsing`: what the parser currently does with Endomondo workout JSON — correctly parsed fields, pinned defects (lost points, missing longitude, type-cast crashes), and error handling for malformed input. This spec intentionally documents current behavior, defects included; the parser-fix change will MODIFY these requirements to intended behavior.

### Modified Capabilities

(none — `export-data-management` is unaffected; fixtures it created are consumed as-is)

## Non-goals

- No parser bug fixes — tests assert today's behavior even where it is clearly wrong.
- No Spring Boot, Java, or other dependency upgrades beyond the Lombok compile fix.
- No TCX parsing tests — no TCX parsing code exists yet.
- No web-layer (MockMvc) test coverage — the parser is exercised directly; controller flow testing can come with the parser fix.

## Preserved vs. changed behavior

- **Preserved**: all runtime behavior. The Lombok bump is compile-time only (generates the same getters/setters/toString); the visibility widening is not observable at runtime.
- **Changed**: nothing behavioral — this change only adds tests and restores compilability.

## Impact

- `pom.xml`: Lombok version property only.
- `UploadController`: one-keyword visibility change on `processEndomondoJson`.
- New test class(es) under `src/test/java/.../controller/`, consuming `src/test/resources/fixtures/`.
- Build: project compiles again on JDK 17; `mvnw test` becomes the verification command for all future changes.
