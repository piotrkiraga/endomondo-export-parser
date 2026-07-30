## Context

See proposal.md - Why. Two facts about the existing code shape this design:

- `MigrationExecutor.resolveAll(archiveRoot)` is purely a function of the archive's contents on disk: `MigrationPlanner.plan(...)` and `WorkoutResolver.resolve(...)` never read `MigrationLedger` at all. The planned action (upload/manual/skip) and the resolved name/location come only from the workout files themselves.
- Ledger-derived state (done/skipped/failed, activity id, confirmed gear) is read separately, fresh, on every request — `MigrationReviewController.workoutView` calls `ledger.find(...)` itself after getting the resolved workout. This already happens independently of `resolveAll` and is untouched by this change.

That means the expensive part (`resolveAll`) and the part that must always be fresh (ledger reads) are already decoupled in the existing code. Caching `resolveAll`'s output introduces no risk of showing a stale migrate/skip decision, because that was never sourced from `resolveAll` in the first place.

## Goals / Non-Goals

**Goals:**
- Compute `resolveAll` at most once per archive root per application run, not once per request.
- Keep the change small: a cache sitting between `MigrationReviewController` and `MigrationExecutor.resolveAll`, no changes to the planner, resolver, or ledger.

**Non-Goals:**
- No cache warming/eager computation at startup — first request after a (re)start still pays the resolution cost, same as today, it's just the *only* request that does.
- No detection of the archive changing on disk mid-run (see proposal.md - Non-goals). If the user edits the archive directory while the app is running, the cache won't see it without a restart — a real (if narrow) behavior change from today's always-fresh-from-disk read, called out under Risks below.

## Decisions

- **A small dedicated `ResolvedPlanCache` service (`service/ResolvedPlanCache`), not `@Cacheable`** — Spring's caching abstraction would need a cache manager dependency (none currently in `pom.xml`) for one call site; a `ConcurrentHashMap<Path, List<ResolvedWorkout>>` behind a two-method class (`get(Path archiveRoot)`, computing via `MigrationExecutor.resolveAll` on first access) is simpler, has no new dependency, and mirrors this project's existing `LocationCache` — an in-memory/disk `@Service` cache already following the same house pattern, just without `LocationCache`'s disk persistence (not needed here since a cold start after restart resolving fresh is the intended behavior, not a gap to fix).
- **Keyed by the normalized archive root path, not a singleton value** — `archiveRootProperty` is effectively constant for the process lifetime (one `@Value`-injected config property), so a single cached value would work too, but keying by path costs nothing, avoids a special case if the archive directory is briefly absent then appears, and matches the existing call signature (`resolveAll(Path archiveRoot)`) exactly.
- **`ConcurrentHashMap.computeIfAbsent` for the cache-fill, no explicit locking** — the review page can receive concurrent requests (e.g. two browser tabs). `computeIfAbsent` guarantees the map never returns a partially-built value; in the rare case its computation function runs more than once under contention (a documented possibility, not a bug, per `ConcurrentHashMap`'s own contract), the result is still correct — just an extra, harmless resolve. Not worth a `ReentrantLock` (as `LocationCache` uses) for a cache that's filled once and then read for the rest of the process's life.
- **No invalidation hook tied to `MigrationLedger` writes** — established in Context: `resolveAll`'s output doesn't depend on the ledger, so a migrate/skip decision has nothing in this cache to invalidate. The spec's "reflected immediately" scenario is a regression guard (proving the cache doesn't accidentally start caching ledger-derived data), not something requiring new invalidation logic.
- **`MigrationReviewController` calls the cache instead of `MigrationExecutor.resolveAll` directly** — both `start()` and `view()` currently call the private `actionable(archiveRoot)` helper, which itself calls `resolveAll`; that helper now reads through `ResolvedPlanCache.get(archiveRoot)` instead, everything downstream (the `.filter(... != SKIP)`) is unchanged.

## Risks / Trade-offs

- [The cache never notices the archive directory changing on disk while the app keeps running] → Accepted per proposal.md's Non-goals: this scenario (editing export files mid-session) was never a design goal to support live, and a restart already resolves fresh, same recovery path as before this change existed.
- [A second, unrelated future caller of `resolveAll` might expect always-fresh output and get a stale one silently] → `MigrationExecutor.resolveAll` itself stays unchanged and uncached; only `MigrationReviewController` routes through `ResolvedPlanCache`. Any future caller keeps calling `resolveAll` directly unless it deliberately opts into the cache, so nothing changes as a hidden side effect.

## Migration Plan

Purely additive: one new class, one call-site change in `MigrationReviewController`. No data migration, no config change, nothing to roll back beyond a code revert.

## Open Questions

None blocking.
