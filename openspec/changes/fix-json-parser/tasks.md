## 1. Parser service on Jackson 3

- [x] 1.1 Create `InvalidWorkoutJsonException` (unchecked, wraps cause) and a parser service (e.g. `EndomondoJsonParser`) implementing merge-then-bind on Jackson 3 (`tools.jackson`): array-of-single-key-objects merged to one node, recursively for `points` entries and `location` (verify: `mvnw clean compile` green)
  - Done. tools.jackson API compiled first try (JsonMapper.builder(), properties(), unchecked JacksonException); module already on classpath via Boot starter.
- [x] 1.2 Correct model types: `EndomondoJson.calories_kcal` and `Point.speed_kmh` → `Double` (verify: compile green)
  - Done.
- [x] 1.3 Wire `UploadController` to the service; catch `InvalidWorkoutJsonException` for the invalid-format message (replacing the `JsonParseException` catch); delete the old map-walking method (verify: compile green; controller contains no Jackson imports)
  - Done. JsonKeys deleted too - its only consumer was the removed loop. Controller has zero Jackson imports.

## 2. Flip the test suite

- [x] 2.1 Update `UploadControllerCharacterizationTest` in place: crash assertions → successful-parse assertions with real values; empty-points assertion → populated-points; exception-type assertions → `InvalidWorkoutJsonException` for both malformed cases; remove "pinned defect" comments; retarget the parser service instead of the controller seam (verify: `mvnw test` green; diff reviewed as the behavioral-change record)
  - Done. Test moved (git mv) to service package as EndomondoJsonParserRegressionTest since it now targets the service directly; all flips in one diff. 7/7 green on first run against the new parser.
- [x] 2.2 Add fixture golden tests: manual fixture → 26 points with both coordinates and null metrics; tracked fixture → 8 points, second point altitude 302.0/distance 0.0/speed 0.0/timestamp non-null (verify: `mvnw test` green)
  - Done. Golden tests included in the regression class; both fixtures parse fully (26 and 8 points, coordinates and metrics verified).
- [x] 2.3 Revert the `processEndomondoJson` package-private seam if the method is gone; controller surface returns to pre-characterization shape (verify: compile green)
  - Done. Old method deleted entirely; no seam remains.

## 3. Web-layer flow test

- [x] 3.1 `@SpringBootTest` + MockMvc: POST a valid fixture to `/upload/process` → upload view with processed info message; POST wrong-shape JSON → upload view with invalid-format error message, not the error page (verify: `mvnw test` green)
  - Done. Required spring-security-test (csrf helper; uncommented the 2020-era block) and spring-boot-webmvc-test (Boot 4 moved AutoConfigureMockMvc to org.springframework.boot.webmvc.test.autoconfigure in its own module). Assertions use HTML-escaped &quot; since Thymeleaf escapes the message quotes.

## 4. Drop the Jackson 2 pin

- [x] 4.1 Remove `com.fasterxml.jackson.core:jackson-databind` from `pom.xml`; confirm nothing else imports `com.fasterxml` (verify: `mvnw clean verify` green; `grep -r com.fasterxml src/` empty)
  - Done. Only com.fasterxml.jackson.annotation imports remain (Jackson 3 still uses the com.fasterxml annotations artifact) - no databind/core usage.

## 5. End-to-end and reconcile

- [x] 5.1 Boot the app and upload a real workout JSON from `data/` through the browser flow via curl (multipart POST) — success message returned (verify: HTTP response contains the processed message)
  - Done. Real 2011 workout uploaded through the packaged jar via curl with CSRF token: processed-successfully message returned.
- [x] 5.2 Reconcile spec deltas against observed behavior; validate; commit (verify: `openspec validate fix-json-parser` passes; `git status` clean; full suite green)
  - Done. Observed behavior matched every spec scenario; no corrections needed. Full suite 10/10 green.
