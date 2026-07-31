## Context

`OsmPlaceLookup.lookup` checks `LocationCache.get` first and returns the cached `PlaceDescription` immediately on any hit, never re-consulting Nominatim for an already-cached coordinate — by design, that's the whole point of the cache. `NominatimClient.reverseGeocode` is already self-throttled to 1 request/second. `LocationCache` has `entries()` (read-all, added for the listing page) and `put(lat, lon, place)` (already used by `OsmPlaceLookup`).

## Goals / Non-Goals

**Goals:**
- Give the user a way to fill in the country for cache entries that predate country tracking, without needing to delete the cache and re-migrate.

**Non-Goals:**
- Any automatic/background backfill — this is an explicit user action, matching the existing "Refresh from Strava" button's pattern (`HomeController.refresh`) of a deliberate, gated, potentially-slow action rather than something that happens implicitly.
- Re-resolving locality/suburb/nearby feature — only the missing country is patched in; everything else about an already-cached entry is left as-is.
- Progress reporting mid-request (e.g. a progress bar) — for this archive's scale (39 entries, ~1/sec) a single synchronous POST completing in well under a minute is acceptable, matching how `RequestThrottleUtil`-throttled batches are already handled elsewhere in this app (e.g. workout report generation's cold-cache run).

## Decisions

- **New `LocationCacheCountryBackfillService`** (`service/` package): one method, `int backfillMissingCountries()`. Iterates `locationCache.entries()`, and for each entry whose `PlaceDescription.countryCode()` is null, parses the coordinate back out of the cache key (same `split(",")`/`Double.parseDouble` approach `LocationCacheController` already uses), calls `nominatimClient.reverseGeocode(lat, lon)`, and — only if a country code comes back — writes a new `PlaceDescription` back via `locationCache.put(lat, lon, updated)` with locality/suburb/nearbyFeature copied unchanged from the original and just the country code replaced. Returns the count of entries actually updated. A failed or country-less re-lookup is silently skipped (that entry just stays as it was) — matches `NominatimClient`'s own existing "never throw, degrade to no result" contract.
- **New `POST /location-cache/backfill-countries`** on `LocationCacheController`, same shape as `HomeController.refresh`: builds `errorMessages`/`infoMessages` lists, calls the service, adds a result message (count updated, or "nothing to fill in" when the count is zero), re-renders the same `location-cache` view with a fresh `groups` model.
- **Template**: a second `<form>` in the card, POST to `/location-cache/backfill-countries`, matching the existing "Refresh from Strava" button's markup shape on `home.html`. `fragments/messages-errors` is added to `location-cache.html` (not previously needed since the page had no actions before this).

## Risks / Trade-offs

- Runtime scales with how many entries are missing a country (1/sec each) — acceptable at this app's real scale (39 entries ≈ well under a minute); if the archive ever grew by orders of magnitude this could become a slow synchronous request, but that's true of the existing workout-report/migration flows too and not a new risk class introduced here.
