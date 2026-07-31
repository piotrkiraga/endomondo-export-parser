## 1. Rate-limit wait visibility

- [x] 1.1 Add a class-level `Logger` to `StravaClient` and log a warning at the moment `withRetryOn429` starts its 429 wait, including the computed wait duration (verify: existing `StravaClientTest` 429/retry tests still pass; manually trigger or reason through the new log line's content)

## 2. Confirmed-gear cache

- [x] 2.1 Add `service/ConfirmedGearCache`, mirroring `LocationCache`'s lock-and-reload-per-call pattern: `Optional<String> get(long activityId)`, `void put(long activityId, String gearId)`, disk-backed at `data/generated/confirmed-gear-cache.json` by default, package-private constructor taking an explicit `Path` for tests (verify: unit test — miss returns empty; put-then-get round-trips including an empty-string gear id; a value written by one instance is visible to a fresh instance pointed at the same file)

## 3. Wire the cache into ConfirmedGearResolver

- [x] 3.1 Inject `ConfirmedGearCache` into `ConfirmedGearResolver`; `gearIdFor(activityId)` keeps its existing always-live behavior but writes the result through to the cache on a successful lookup (verify: unit test asserts a live lookup populates the cache; existing `ConfirmedGearResolverTest` cases still pass unchanged)
- [x] 3.2 Add `Optional<String> cachedGearIdFor(long activityId)` that reads only from `ConfirmedGearCache`, making no Strava call, empty `Optional` on a miss (verify: unit test — cache hit returns the value with zero server expectations set on the mock rest server; cache miss returns empty)

## 4. Wire the cache into WorkoutReportGenerator

- [x] 4.1 `confirmedGearDisplayFor` calls `cachedGearIdFor` first; only on a miss does it call `throttle.await()` followed by `gearIdFor` (verify: unit test with a pre-populated `ConfirmedGearCache` asserts no Strava call and no throttle wait for a cached activity id; a test with an empty cache asserts the existing throttle + live-call behavior is unchanged)

## 5. Test/rig updates

- [x] 5.1 Update every direct `new ConfirmedGearResolver(...)` call site (`ConfirmedGearResolverTest`, `WorkoutReportGeneratorTest`, `MigrationTestSupport`) to supply a temp-dir-backed `ConfirmedGearCache`

## 6. Validation

- [x] 6.1 Full `mvnw test` green; `openspec validate speed-up-workout-report --strict` passes
- [x] 6.2 Live-check against the real archive: generate the workout report twice in a row while connected to Strava; confirm the second generation is fast (no multi-minute wait) and the app log shows a rate-limit-wait log line if a 429 is hit on the first generation — confirmed: run 1 (cold cache) took 954s and hit the 429 wall, logging "Strava rate limit hit (429); waiting 766s for the next rate-limit window before retrying"; run 2 (warm cache) took 4s
