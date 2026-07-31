## Context

`LocationCacheController.summariesByCoordinate()` currently builds `Map<CachedCoordinate, String>`, one entry per cached coordinate, sorted by coordinate string. `PlaceDescription.summary(Locale)` already produces the "suburb, locality, country" text used per row. Real archives cluster around a handful of places, so many rows show identical text.

## Goals / Non-Goals

**Goals:**
- Show one row per distinct resolved place, with its coordinate count and the coordinates that map to it, instead of one row per coordinate.

**Non-Goals:**
- Changing `LocationCache`'s storage or `PlaceDescription.summary(Locale)` — this only reshapes display, grouping on the same summary text already produced today.
- Merging places that are textually different but geographically close (e.g. two suburbs of the same city that resolve to different summaries) — grouping is by exact summary text equality, nothing fuzzier.

## Decisions

- **New `LocationGroup` record** (`model/LocationGroup(String place, List<CachedCoordinate> coordinates)`), with a `count()` method (`coordinates.size()`) for template convenience. Lives in `model/` alongside `CachedCoordinate`, `PlaceDescription`.
- **Grouping key is the exact `summary(Locale)` string.** Two coordinates group together iff their rendered summaries are identical for the current request's locale — simplest correct definition of "same place" given what's already computed, and it naturally handles the "no country" degraded case (those still group correctly among themselves, just without a country segment).
- **`LocationCacheController` groups via a `TreeMap<String, List<CachedCoordinate>>`** keyed by summary text (alphabetical iteration order for free, matching the proposal's "ordered alphabetically" bullet), then converts to `List<LocationGroup>`. Coordinates within a group are sorted (latitude then longitude) for stable, deterministic output.
- **`CachedCoordinate` gains a `display()` method** (`"%.3f,%.3f".formatted(latitude, longitude)`, matching `LocationCache`'s own key format) so the template can render each coordinate without reaching into two separate fields — keeps the template simple for what's now a nested per-group list rather than a single flat table cell.
- **Template**: same `.table.table-sm` pattern, now three columns (place, count, coordinates) instead of three columns (latitude, longitude, place). The coordinates column joins each group's `display()` values with a comma via a nested `th:each` with a trailing-comma check on `iterStat.last`.
- **i18n**: `locationCache.latitude`/`locationCache.longitude` keys are removed (no longer rendered); new `locationCache.count` and `locationCache.coordinates` column-header keys added; `locationCache.description` reworded to describe places rather than coordinates.

## Risks / Trade-offs

- None blocking. Grouping by exact string equality is a hash-map operation over at most a few hundred entries — no performance concern at this scale.
