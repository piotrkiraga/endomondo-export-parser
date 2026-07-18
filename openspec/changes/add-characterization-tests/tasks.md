## 1. Restore compilability

- [x] 1.1 Set Lombok version to 1.18.30 in `pom.xml` (verify: `mvnw clean compile` succeeds on JDK 17)
  - Done. `mvnw clean compile` succeeds on JDK 17.0.2.
- [x] 1.2 Run the existing test suite as a smoke check (verify: `mvnw test` — record result; the context-loads test may reveal further toolchain issues to document, not fix, unless they block running the new tests)
  - Done. Existing context-loads test passes; no further toolchain issues (`mvnw test`: 1/1 green before new tests).

## 2. Create the test seam

- [x] 2.1 Widen `UploadController.processEndomondoJson` from `private` to package-private; no other edits (verify: `mvnw clean compile` still succeeds; `git diff` shows exactly one keyword change)
  - Done. Single-keyword diff; compile stays green.

## 3. Write characterization tests (observe first, then assert)

- [x] 3.1 Probe harness: test class instantiating `UploadController` directly, loading fixtures via the test classpath into `MockMultipartFile` (verify: a trivial probe test executes the parser and prints/records its outcome)
  - Done. Probe test executed parser against fixtures + 5 crafted inputs; outcomes recorded, probe deleted afterwards.
- [x] 3.2 Pin behavior for `workout-manual.json`: exception type or returned object, including which fields are populated before any crash (verify: `mvnw test` green with assertions matching observation)
  - Done. Observed: ClassCastException (Double->Integer at calories_kcal); pinned in `UploadControllerCharacterizationTest`.
- [x] 3.3 Pin behavior for `workout-tracked.json` (verify: `mvnw test` green)
  - Done. Same ClassCastException; pinned.
- [x] 3.4 Pin behavior for malformed inputs: non-JSON bytes and valid-JSON-wrong-shape `{}` (verify: `mvnw test` green)
  - Done. Non-JSON -> JsonParseException; `{}` -> MismatchedInputException, as predicted.
- [x] 3.5 Pin points behavior with a minimal inline JSON document that avoids any crashing entries, asserting the points outcome on the returned object (verify: `mvnw test` green)
  - Done. Points remain empty on the returned object; also pinned empty-array degenerate case and scalar parsing via a type-conforming inline document.

## 4. Reconcile spec with observation

- [x] 4.1 Compare observed behavior against `specs/workout-json-parsing/spec.md`; correct any scenario the observations contradict (verify: `openspec validate add-characterization-tests` passes and every spec scenario maps to a green test)
  - Done. One scenario corrected: fixture header-field observation is impossible (crash discards result); replaced with type-conforming-document scenario. All other predictions matched observation.
- [x] 4.2 Commit implementation + spec corrections (verify: `git status` clean; `mvnw test` green from a clean checkout state)
  - Done. `mvnw test`: 8/8 green.
