## 1. Cache component

- [x] 1.1 Add `service/ResolvedPlanCache`: a `ConcurrentHashMap<Path, List<ResolvedWorkout>>`-backed cache with `List<ResolvedWorkout> get(Path archiveRoot, MigrationExecutor executor)` (or constructor-injected `MigrationExecutor`), filling via `computeIfAbsent` calling `executor.resolveAll(archiveRoot)` (verify: `mvnw compile`)

## 2. Wire into the review page

- [x] 2.1 `MigrationReviewController`'s `actionable(Path)` reads through `ResolvedPlanCache` instead of calling `executor.resolveAll` directly; `MigrationExecutor.resolveAll` itself is untouched (verify: unit test asserting a second call for the same archive root does not increase a resolve-call counter — e.g. a test-only `MigrationExecutor` spy/wrapper, or asserting on a fake resolver's invocation count)

## 3. Regression coverage

- [x] 3.1 Test: after a migrate or skip decision made through `MigrationReviewController`'s existing POST handlers, the next view of that workout (or the `start` redirect) reflects the new ledger status, proving the cache never shows a stale decision (verify: `mvnw test`)
- [x] 3.2 Test: `ResolvedPlanCache.get` called twice with a different archive root each time resolves each root independently and caches both (verify: `mvnw test`)

## 4. Validate

- [x] 4.1 Full `mvnw test` green; manually confirm in the running app that clicking Next/Previous through the review page feels immediate after the first load (verify: `openspec validate migration-review-plan-cache --strict` passes)
