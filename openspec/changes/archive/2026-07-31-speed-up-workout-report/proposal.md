## Why

Generating the offline workout report over a real archive can silently freeze for up to 15 minutes with zero feedback, and does this every single time the report is regenerated. Root cause (diagnosed on GitHub issue #24): `WorkoutReportGenerator` reads back each already-migrated workout's confirmed gear with a live, throttled Strava call — a real archive can have well over 100 such workouts, Strava's actual rate limit is 100 requests/15 minutes, and `StravaClient.withRetryOn429` has no logging in its retry path, so hitting that wall looks indistinguishable from a hang. On top of that, nothing is cached across generations, so every regeneration re-pays the same cost even when nothing has changed.

## What Changes

- `StravaClient.withRetryOn429` logs when a 429 wait begins and how long it will be, so a multi-minute wait is visible in the app log instead of looking like a hang.
- A new disk-backed `ConfirmedGearCache` (same lock-and-reload pattern as `LocationCache`) persists each activity's confirmed gear id once successfully read from Strava.
- `WorkoutReportGenerator`'s confirmed-gear lookups check this cache first and skip the network call (and its throttle wait) entirely on a hit; a miss falls back to the existing live read, which also warms the cache.
- The review page's confirmed-gear read (`MigrationReviewController`, via `ConfirmedGearResolver.gearIdFor`) stays live on every call, unchanged — that's the one place a user actually verifies a just-applied Strava-side gear correction, so it must never show a stale cached value. It also writes through to the cache, so visiting the review page for a workout keeps that workout's cached entry fresh for the next report generation.

## Capabilities

### Modified Capabilities
- `strava-migration`: the rate-limit-wait requirement gains a logging expectation; the confirmed-gear requirement gains a scenario distinguishing the review page's always-live read from the workout report's cache-eligible read.

## Impact

- `StravaClient` (`withRetryOn429`): adds a log statement, no behavior change to retry/wait logic itself.
- New `service/ConfirmedGearCache` class + its own test.
- `ConfirmedGearResolver`: gains a cache-aware lookup path used only by `WorkoutReportGenerator`; `gearIdFor` (the live path used by the review page) additionally writes through to the cache on success.
- `WorkoutReportGenerator`: switches its confirmed-gear lookup to the cache-aware path; no change to its throttle usage for the (now rarer) cache-miss case.
- Test/rig updates wherever `ConfirmedGearResolver` is constructed directly (`ConfirmedGearResolverTest`, `WorkoutReportGeneratorTest`, `MigrationTestSupport`).
- New cache file `data/generated/confirmed-gear-cache.json` (git-ignored, same as `location-cache.json`).
