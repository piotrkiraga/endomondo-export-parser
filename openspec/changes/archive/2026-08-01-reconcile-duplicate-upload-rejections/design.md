## Context

`migrate-to-strava/design.md` (2026-07-29) made a deliberate call: "Rather than pattern-match strings that could change without notice, `StravaUploadResultDto.hasActivityId()` alone decides success — if a duplicate rejection does carry an id (community-observed, not contractual), it is treated as success automatically." That bet turned out wrong for at least two real ledger entries: their duplicate rejections carried no `activity_id`, so `StravaUploadResultDto.failed()` (`activityId == null && error != null && !error.isBlank()`) fired and the workout was recorded `FAILED` even though it is confirmed present on Strava (both spot-checked by hand). `LedgerEntry`'s own javadoc already anticipates this should work — "finalized DONE (with the resulting activity id, whether from a fresh upload or Strava's own duplicate-upload rejection — the ledger records whatever id it's given)" — but nothing in the code ever actually supplies that id on a duplicate rejection today.

`ConfirmedGearResolver` already establishes the pattern this change follows: don't trust what a write call's response claims (or fails to claim) — read the truth back from Strava directly. That's the model applied here, rather than reviving the string-matching approach the original design explicitly rejected.

## Goals / Non-Goals

**Goals:**
- Recover the real activity id for a duplicate-rejected upload by reading it back from Strava, so the ledger reflects reality instead of a false `FAILED`.
- Never record a wrong activity id — an unconfirmed match must still fail loudly, not guess.

**Non-Goals:**
- Parsing Strava's `error` string for any signal (e.g. "duplicate", an embedded id). Still explicitly rejected, same reasoning as the original design: undocumented, could change without notice. The read-back approach below needs no assumption about that text at all — only the already-documented fact that `activity_id` came back null is used as the trigger.
- Retrying or backing off around the reconciliation read — it's a single bounded query (see Decisions), already inside the same throttle/retry-on-429 machinery every other `StravaClient` call uses.
- Reconciling `INPUT_MANUAL` (create) workouts — they never hit this code path; see proposal.md's non-goals.

## Decisions

- **New `StravaClient.listActivities(Instant after, Instant before)`** — thin wrapper over `GET /athlete/activities?after=<epoch>&before=<epoch>&per_page=30`, returning `List<StravaActivitySummaryDto>`. Uses the existing `activity:read_all` scope (already requested for the gear read-back), so no reconnect is ever required for this feature. `per_page=30` is generous for what should realistically be a 0-1-activity result in a several-minute window; no pagination handling needed.
- **New `StravaActivitySummaryDto(Long id, String startDate, Double distance)`** — `start_date` kept as the raw ISO-8601 string (`"start_date")`, parsed to `Instant` via a `startDateInstant()` helper method on the DTO (`Instant.parse(...)`), matching this codebase's existing convention of manual date parsing over a Jackson date module (see `StravaTokensDto`'s epoch-seconds field, `MigrationExecutor.toStartInstant`) rather than introducing a new deserialization dependency for one field.
- **New `DuplicateActivityResolver` service** (mirrors `ConfirmedGearResolver`'s role — matching logic lives in a dedicated resolver, not in `StravaClient` or `MigrationExecutor` directly): `Optional<Long> findExistingActivity(Instant startTime, Double distanceKm)`.
  - Queries `listActivities(startTime.minus(2min), startTime.plus(2min))` — a window this narrow keeps the match to what should realistically be exactly the one activity a genuine duplicate corresponds to, since Strava's own duplicate detection is itself keyed off the uploaded file's content (so a real duplicate's stored `start_date` is identical to the archive's, not approximate).
  - Filters to activities whose `startDateInstant()` exactly equals `startTime` (to the second — both sides derive from the same original TCX timestamp, so exact equality is the correct bar, not a tolerance window).
  - If exactly one activity matches by start time: when the workout's `distanceKm` is known and the matched activity's `distance` is known, requires them within 50m of each other as a second, independent safety check before trusting the match; if either is unknown, the start-time match alone stands.
  - Returns that activity's id only when the checks above pass; returns empty in every other case (zero matches, more than one match, or a distance mismatch) — ambiguity always resolves to "not confirmed," never a guess.
- **`MigrationExecutor.uploadAndPoll`**: when `result.failed()` is true (today's unconditional-failure branch), calls `duplicateActivityResolver.findExistingActivity(toStartInstant(workout), workout.distanceKm())` before throwing. A present result returns that id exactly like a normal successful upload (ledger `DONE`, trailing metadata-update call runs as usual). An empty result throws `StravaApiException` exactly as today — behavior for a genuine failure (bad file, auth, unrelated Strava error) is unchanged.

## Risks / Trade-offs

- **Extra Strava call on every genuine failure, not just duplicates.** The resolver call happens whenever `result.failed()` is true, which includes real failures too (a bad file, for instance) — one extra throttled `GET` before the workout is (correctly) marked failed anyway. Acceptable: it's one bounded, cheap call, already inside the existing throttle, and the alternative (trying to distinguish "duplicate" failures from other failures up front) is exactly the string-matching this design deliberately avoids.
- **A 2-minute window could theoretically miss or double-match** if the athlete has two of their own activities starting within 2 minutes of each other — the exact-second start-time filter after the window query is the actual match criterion, not the window itself, so this only matters if two activities share the identical second, which the distance safety check further guards against. Considered narrowing the window further; kept at 2 minutes since Strava's own timestamp precision/rounding on `start_date` isn't documented either, and a slightly wider window costs nothing when the exact-match filter still applies.
