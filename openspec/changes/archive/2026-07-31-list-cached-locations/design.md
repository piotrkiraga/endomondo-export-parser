## Context

`LocationCache` stores resolved places keyed by a coordinate string (`"%.3f,%.3f"`, 3-decimal rounding, ~110m). Reads/writes go through a `ReentrantLock`-guarded lazy-loaded `Map<String, PlaceDescription>`. Today its public API is get/put/size only — no way to enumerate entries. `PlaceDescription` already has a `phrase()` helper (e.g. "along Vistula in Dębniki, Kraków") that formats locality/suburb/nearbyFeature for display.

## Goals / Non-Goals

**Goals:**
- Let the user see every cached coordinate and its resolved place from a page reachable off the Home dashboard's "Cached locations" stat.
- Keep `LocationCache`'s storage format and existing get/put/size behavior untouched — this is a read-only addition.

**Non-Goals:**
- Tracking which workout(s) produced a given cache entry. Not stored today; adding it would mean changing the cache's persisted shape (a schema change to `location-cache.json`, plus every `put` call site would need to start passing workout context it doesn't currently have). The issue itself flagged this as worth scoping out, and it is: out of scope for this change.
- Editing or invalidating cache entries from the UI — this is a read-only listing, same spirit as the existing gear table on Home (also read-only).
- Pagination — the archive is a personal export; cache sizes are in the tens to low hundreds of entries, not a scale where an unpaginated table is a problem (same assumption the existing gear table already makes).

## Decisions

- **`LocationCache.entries()` returns `Map<String, PlaceDescription>`** (an unmodifiable copy of the loaded map, taken under the same lock as `get`/`put`), not a richer DTO — the coordinate key is parsed back into lat/lon at the template/controller boundary, not inside `LocationCache` itself. Keeps the cache class's responsibility unchanged (storage), pushes display shaping to the controller, consistent with how `HomeController` already shapes `AppStatus` for display rather than services doing it.
- **New `LocationCacheController` at `GET /location-cache`**, following the same shape as `HomeController`/`MigrationController`: `@Controller` extending `BaseController`, single GET handler building a `ModelAndView` for a new `templates/location-cache.html`. Parses each entry's key (`"lat,lon"`) into two doubles for display, sorted for stable output (by coordinate string, ascending — simplest stable order, no natural "importance" ordering exists).
- **Template reuses the existing `.table.table-sm` pattern** from Home's gear table: columns for latitude, longitude, and the resolved place (`entry.value.phrase()`). Standard page scaffold (`fragments/head`, `fragments/header`, `fragments/messages-errors`, `fragments/strava-status`, `fragments/footer`) matching every other content page.
- **No new navbar entry.** Per the issue, this is reached via the Home dashboard's "Cached locations" link, not a top-level nav item — consistent with how the retired Strava Dictionary page's replacement (the Home gear table) isn't a separate nav destination either.
- **`home.html`'s "Cached locations" `detail-value` becomes `<a th:href="@{/location-cache}" th:text="${appStatus.locationCacheSize}">`** — the count itself is the link text, no separate icon or "view" label needed; matches the existing terse `detail-list` styling.

## Risks / Trade-offs

- `entries()` loads the full map into memory to build the page — already true of every other `LocationCache` operation (the whole map is loaded lazily on first use regardless), so no new risk.
- None blocking.
