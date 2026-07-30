## Why

The one-workout-at-a-time migration review page (`/migration/review/{index}`, and its `start` entry point) recomputes the entire migration plan on every single request: `MigrationReviewController` calls `MigrationExecutor.resolveAll`, which re-scans the whole archive, re-runs the planner, and re-resolves every workout's display name via `PlaceLookup` (OpenStreetMap lookups) — all of that just to render the one workout at the requested index. Every click of Next/Previous, and every fresh visit, pays for the whole archive's worth of work. This was a deliberate tradeoff for statelessness (survive a restart, keep URLs bookmarkable, always reflect the ledger's true state) but recomputing from scratch on every request is a stronger guarantee than statelessness actually requires, and it makes the review page — the primary hands-on migration workflow — feel sluggish on every interaction as the archive grows.

## What Changes

- The resolved plan (`MigrationExecutor.resolveAll`'s result) is cached in memory across requests instead of being recomputed on every one.
- The cache is invalidated whenever something it depends on changes: a migrate/skip decision made through the review page itself (a ledger write), so the page never shows a stale decision it just made.
- The cache is process-local (in-memory only) — an app restart naturally starts cold and rebuilds it on first access, preserving today's "survives a restart" property without needing any persistence of its own.
- No change to what the planner or resolver compute, and no change to what the review page shows the user — this is purely a caching layer around an existing, unchanged computation.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `strava-migration`: adds a requirement that the review page's resolved plan is cached across requests rather than recomputed from scratch each time, while staying correct immediately after a migrate/skip decision made through the same page.

## Non-goals

- No change to the planner's or resolver's logic, output, or the offline reports (`WorkoutReportGenerator`, `PhotoReportGenerator`) — they already generate once to a file and are unaffected by how the interactive review page resolves its own in-memory plan.
- No persistence of the cache across an app restart — a cold cache on restart, rebuilt on first access, is the existing and still-intended behavior.
- No change to how a decision made *outside* the app (e.g. directly editing the ledger file by hand) is detected — that was never guaranteed to be picked up without a restart before this change, and isn't a goal to add now.

## Characterization vs. Change

- Preserved: the review page's displayed content, the planner/resolver's output for a given archive+ledger state, and the "recompute from source of truth, don't trust stale state" correctness guarantee for the page's own writes (migrate/skip).
- Preserved: cold start after a restart still resolves fresh, exactly as today.
- Changed: the plan is computed once and reused across requests instead of on every request, cutting review-page latency from O(total workouts) to O(1) per click after the first request warms the cache.

## Impact

- `controller/MigrationReviewController` (reads from the cache instead of calling `MigrationExecutor.resolveAll` directly on every request)
- A new small caching component (exact shape decided in design.md) sitting between the controller and `MigrationExecutor`/`MigrationLedger`
- No changes to `service/MigrationPlanner`, `service/WorkoutResolver`, `service/PlaceLookup`, the offline report generators, or any Strava-facing code
