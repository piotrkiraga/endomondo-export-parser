## 1. Model support

- [x] 1.1 Test: `CachedCoordinate.display()` renders "%.3f,%.3f" (matching `LocationCache`'s own key format) — new `CachedCoordinateTest`, including a case pinned under a Polish default locale
- [x] 1.2 Add `display()` to `CachedCoordinate` — judgment call: implemented as `String.format(Locale.ROOT, "%.3f,%.3f", ...)` rather than `"%.3f,%.3f".formatted(...)`. `formatted` uses the JVM default locale, which on this machine is `pl-PL` and would render "50,061,19,937" (comma decimal separator, making the pair unreadable); `Locale.ROOT` is also what `LocationCache.key` itself uses, so this genuinely matches the cache's key format.
- [x] 1.3 Add `LocationGroup(String place, List<CachedCoordinate> coordinates)` record with a `count()` method

## 2. Group the controller's model by place

- [x] 2.1 Test: `LocationCacheController`'s model groups multiple coordinates that share a summary into one `LocationGroup`, with the right count and coordinate list — three Kraków coordinates (one via a `PlaceDescription` that differs only in its `nearbyFeature`, which `summary` ignores) collapse into one group of 3, coordinates in latitude-then-longitude order
- [x] 2.2 Test: groups are ordered alphabetically by place summary
- [x] 2.3 Test: an empty cache still renders the empty state (model attribute name will change — update any test relying on the old `entries` map attribute) — whole `LocationCacheControllerTest` rewritten against the `groups` list; the old sorted-by-coordinate test is dropped, replaced by 2.2's sorted-by-place assertion
- [x] 2.4 Implement grouping in `LocationCacheController`: build via a `TreeMap<String, List<CachedCoordinate>>` keyed by `summary(Locale)`, sort each group's coordinates (latitude then longitude), convert to `List<LocationGroup>`

## 3. Template and i18n

- [x] 3.1 Update `templates/location-cache.html`: three columns (place, count, coordinates), coordinates joined via nested `th:each` with a trailing-comma check
- [x] 3.2 Remove now-unused `locationCache.latitude`/`locationCache.longitude` keys from `messages_en.properties`/`messages_pl.properties`; add `locationCache.count`/`locationCache.coordinates`; reword `locationCache.description` — "Count"/"Liczba" and "Coordinates"/"Współrzędne". `locationCache.heading` ("Resolved coordinates"/"Rozpoznane współrzędne") left as-is: not listed in this task, and the page still lists resolved coordinates, only grouped.

## 4. Finalize

- [x] 4.1 Full `mvnw test` green — `mvnw.cmd -B verify`: 303 tests, 0 failures
- [x] 4.2 Live-verify: run the app, confirm `/location-cache` now shows one row per distinct place (e.g. the real "Dębniki, Kraków" cluster collapses into one row with its coordinate count), not one row per coordinate — ran on port 8099 (port 8080 was already in use) against the real `data/generated/location-cache.json`, read-only. 39 cached coordinates rendered as 14 place rows, alphabetically ordered: "Dębniki, Kraków" is a single row with count 19 and its 19 coordinates listed latitude-then-longitude, "Stare Miasto, Kraków" 3, "Podgórze, Kraków" 2, the rest 1–2. No country segment on any summary, as expected — every real entry predates country tracking.
- [x] 4.3 `openspec validate group-cached-locations-by-place --strict` passes
