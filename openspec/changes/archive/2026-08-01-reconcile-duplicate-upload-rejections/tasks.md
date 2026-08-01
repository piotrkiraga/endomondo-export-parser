## 1. Strava client and DTO

- [x] 1.1 Test: `StravaClient.listActivities(after, before)` sends `GET /api/v3/athlete/activities` with the correct `after`/`before` epoch-second query params and bearer token, via `MockRestServiceServer` (this project's existing pattern — no Mockito)
- [x] 1.2 Test: `listActivities` deserializes a JSON array response into `List<StravaActivitySummaryDto>`, and returns an empty list for an empty array
- [x] 1.3 Test: `StravaActivitySummaryDto.startDateInstant()` parses Strava's ISO-8601 `start_date` string correctly
- [x] 1.4 Implement `StravaActivitySummaryDto` and `StravaClient.listActivities` — judgment call: `startDateInstant()` returns `null` for an absent `start_date` rather than throwing, so a response missing that field can never blow up the resolver's filter with a `NullPointerException` (which would escape `migrateOne`'s `catch (StravaApiException)` and abort a whole run)

## 2. DuplicateActivityResolver

- [x] 2.1 Test: `findExistingActivity` returns the activity id when exactly one activity's start time exactly matches and distance is within tolerance — fixture data mirrors `workout-tracked.json` (start `2011-09-10T12:58:00Z`, 34.04 km), plus a companion test for a 22 m difference still counting as the same activity
- [x] 2.2 Test: returns empty when zero activities match the start time — covered twice: a windowed activity starting at a different second, and an empty activity list
- [x] 2.3 Test: returns empty when more than one activity matches the start time (ambiguous)
- [x] 2.4 Test: returns empty when the single start-time match's distance differs from the expected distance by more than the tolerance
- [x] 2.5 Test: a start-time match is trusted when the workout's distance is null (no track distance known) or the matched activity's distance is null (distance check skipped, not treated as a mismatch)
- [x] 2.6 Implement `DuplicateActivityResolver` — judgment call: a failing read-back (`StravaApiException`) is deliberately *not* caught here, unlike `ConfirmedGearResolver#gearIdFor`; the design enumerates exactly three empty-results (zero matches, several matches, distance mismatch), and letting the API error propagate keeps the workout `FAILED` with the read-back's own diagnosis rather than swallowing it

## 3. MigrationExecutor integration

- [x] 3.1 Test: `uploadAndPoll`'s duplicate-rejection path (`StravaUploadResultDto.failed()` true) calls the resolver and, on a present result, returns that activity id — ledger ends up `DONE`, trailing metadata-update call still runs
- [x] 3.2 Test: when the resolver returns empty, the workout still ends up `FAILED` with the original upload error as the reason — unchanged from today's behavior
- [x] 3.3 Test: a genuine failure unrelated to duplicates (resolver correctly returns empty since no matching activity exists) still fails with the original error message, not a generic one
- [x] 3.4 Implement the `uploadAndPoll` change — judgment calls: (a) `DuplicateActivityResolver` is a constructor-injected collaborator, exactly like the existing `OldBikeGearResolver` (added to both the `@Autowired` constructor and the package-private test constructor; the three test rigs and two `super(null, ...)` stubs were updated accordingly); (b) the reconciliation read is preceded by `throttle.await()`, like every other Strava call this class makes, so the class's documented ~1 request/second posture still holds

## 4. Finalize

- [x] 4.1 Full `mvnw test` green — `mvnw.cmd -B verify`: 329 tests, 0 failures, 0 errors, 0 skipped
- [x] 4.2 `openspec validate reconcile-duplicate-upload-rejections --strict` passes
