## Context

See proposal.md - Why. Two independent problems, one fix each: `StravaClient.withRetryOn429` (`StravaClient.java:369-388`) sleeps up to ~15 minutes on a 429 with no logging anywhere in that path; `WorkoutReportGenerator.confirmedGearDisplayFor` (`WorkoutReportGenerator.java:122-130`) calls `ConfirmedGearResolver.gearIdFor`, a live throttled Strava call, once per already-migrated workout, every single report generation, with no caching. `ConfirmedGearResolver.gearIdFor` is shared with `MigrationReviewController` (the interactive per-workout review page), which needs a live read every time — it's the one place in the app a user actually verifies a just-applied Strava-side gear correction (see README's "A note on the data").

## Goals / Non-Goals

**Goals:**
- A 429 wait is visible in the app log the moment it starts, with its expected duration.
- Regenerating the workout report after a first successful generation makes zero Strava calls for gear that was already confirmed, unless that workout's ledger status changed since.
- The review page's confirmed-gear read stays exactly as live and trustworthy as it is today — no regression on the one workflow (manual Strava-side gear correction, verified on the review page) this project already depends on.

**Non-Goals:**
- No UI-visible progress indicator for the 429 wait — a log entry is the agreed minimum fix (per the issue's own framing); a spinner/progress bar is a separate, bigger change if ever wanted.
- No cache expiry/TTL — gear on a migrated activity essentially never changes except by deliberate manual correction, and the review-page write-through (below) already keeps the cache fresh wherever a user actually looks.
- No change to `RequestThrottleUtil` or the 1 req/sec throttle rate itself — the cache makes most calls unnecessary; the ones that still happen still need to stay under Strava's limit.

## Decisions

- **Log the 429 wait in `StravaClient.withRetryOn429` itself, not in each caller.** Every Strava call already funnels through this one method, so one log statement covers uploads, activity updates, gear lookups, and everything else uniformly. Alternative rejected: logging in `WorkoutReportGenerator`'s loop specifically — would miss every other call site (migration executor, review page, dictionary refresh) that can hit the same wall.
- **New `ConfirmedGearCache` service, keyed by activity id → gear id, mirroring `LocationCache`'s existing lock-and-reload-per-call pattern exactly** (a `ReentrantLock`, lazy on-disk load into an in-memory map, write-through save) rather than an in-memory-only cache or a new persistence abstraction. Consistent with the one caching pattern this codebase already has, and — like `LocationCache` — must survive an app restart, since a fresh app instance regenerating the report should still get the speed-up from a prior run.
- **Two access paths on `ConfirmedGearResolver`, not one cache-transparent method.** `gearIdFor(activityId)` (used by the review page) keeps its current always-live behavior unchanged, but now also writes its result through to `ConfirmedGearCache` on success. A new `cachedGearIdFor(activityId)` reads only from `ConfirmedGearCache` — no network call, empty `Optional` on a miss. `WorkoutReportGenerator` calls `cachedGearIdFor` first and only calls `throttle.await()` + `gearIdFor` on a miss, so throttle ownership stays exactly where it already is (`WorkoutReportGenerator`) instead of moving into the resolver, where it would also wrongly apply to the review page's single per-click lookup. Alternative rejected: one cache-first method that internally falls back to the live call — would either hide the throttle call inside the resolver (forcing it onto the review page's every click too) or leave `WorkoutReportGenerator` unable to skip the throttle wait on a hit. Also rejected: making `gearIdFor` itself cache-first — would mean the review page could show a stale cached value right after a user manually fixes gear on Strava's site and comes back to verify it, which is the one workflow this project explicitly already relies on (see Context).
- **`WorkoutReportGenerator` skips `throttle.await()` entirely on a `cachedGearIdFor` hit.** The throttle exists to pace real network calls; a cache hit makes none, so pacing it would reintroduce exactly the slowness this change removes for no reason.
- **Cache value is the bare gear id string (or empty string for confirmed-no-gear), matching `ConfirmedGearResolver.gearIdFor`'s own `Optional<String>` semantics** (empty Optional = lookup failed, present-empty-string = confirmed no gear) — no new type needed; the cache stores a plain `Map<String, String>` keyed by `String.valueOf(activityId)`, mirroring `LocationCache`'s own `Map<String, PlaceDescription>` shape.

## Risks / Trade-offs

- [A workout's gear changes on Strava's side, but the workout's review page is never revisited] → the workout report will keep showing the stale cached value indefinitely. Accepted: this mirrors `LocationCache`'s own existing risk note (no in-memory-state staleness bug across processes) and the correction workflow already funnels through the review page in practice; deleting `data/generated/confirmed-gear-cache.json` is the manual escape hatch, same as `location-cache.json` today.
- [First-ever report generation over a large archive still hits the 429 wall at least once, since nothing is cached yet] → unavoidable without changing what's fetched; the logging fix (problem 1) is what makes that first run's wait visible rather than looking hung, which is the actual complaint in issue #24.

## Migration Plan

Additive plus one behavior-preserving change: new `ConfirmedGearCache` class and its own test; `ConfirmedGearResolver` gains a second lookup method and a write-through on the existing one; `WorkoutReportGenerator` switches which method it calls; `StravaClient` gains one log statement. No persisted data migration — `data/generated/confirmed-gear-cache.json` starts empty and fills in as reports are generated, same bootstrap behavior `location-cache.json` already has. Test/rig call sites constructing `ConfirmedGearResolver` directly (`ConfirmedGearResolverTest`, `WorkoutReportGeneratorTest`, `MigrationTestSupport`) need the new constructor argument. Rollback is a code revert; the cache file is safe to leave behind or delete either way.
