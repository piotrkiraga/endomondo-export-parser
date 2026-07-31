## 1. Capture the country code from Nominatim

- [x] 1.1 Test: `NominatimClient.reverseGeocode` returns a `Locality` whose `countryCode` matches the response's `address.country_code`, uppercased (verify: unit test with `MockRestServiceServer`, mirroring existing `NominatimClientTest` style)
- [x] 1.2 Test: a response missing `country_code` yields a `Locality` with a null `countryCode` (not an error)
- [x] 1.3 Add `countryCode` to the `Locality` record; update `NominatimClient.parse()` to extract it (uppercased ISO alpha-2) — extracted by its own private `countryCode(JsonNode)` rather than the existing `firstNonBlank` helper, which runs values through `OsmNamesUtil.primary` (bilingual-name trimming) — wrong treatment for a two-letter code

## 2. Thread the country code into PlaceDescription

- [x] 2.1 Add `countryCode` as a 4th field on `PlaceDescription`; update `OsmPlaceLookup` to pass `locality.get().countryCode()` through
- [x] 2.2 Update every other `new PlaceDescription(...)` call site (tests) to pass a 4th argument — `null` where the test isn't exercising country behavior — 8 test files, all passing `null`
- [x] 2.3 Test: `LocationCache` round-trips a `PlaceDescription` with a non-null `countryCode` through save/load (JSON persistence covers the new field) — round-trips through a second `LocationCache` instance over the same file, so the assertion covers real serialization rather than the in-memory map
- [x] 2.4 Test: `LocationCache.entries()`/`.get()` deserializes an on-disk entry that has no `countryCode` field at all (simulating a pre-existing cache entry) into a `PlaceDescription` with `countryCode == null`, not an error — hand-written JSON fixture in the test, loaded through the package-private temp-dir constructor

## 3. `PlaceDescription.summary(Locale)`

- [x] 3.1 Test: `summary(Locale)` renders "suburb, locality, country" when all three are present, country name resolved via `Locale.getDisplayCountry(displayLocale)` for the given code, in both an English and a Polish `displayLocale` — "Dębniki, Kraków, Poland" / "Dębniki, Kraków, Polska"; a second test pins that `summary` ignores `nearbyFeature`, unlike `phrase()`
- [x] 3.2 Test: `summary(Locale)` omits the country segment when `countryCode` is null — plus an unresolvable-code case. Judgment call: the design assumed an unrecognised code is echoed back unchanged, which holds for genuinely unassigned codes (`QQ`), but the JDK's Common Locale Data Repository data does resolve the reserved code `ZZ` to "Unknown Region"; the test therefore uses `QQ`, and a stored `ZZ` would display as "Unknown Region". Not worth special-casing — Nominatim returns real ISO 3166-1 alpha-2 codes.
- [x] 3.3 Test: `summary(Locale)` omits `suburb` when null/blank/equal to `locality`, same rule `phrase()` already uses
- [x] 3.4 Implement `summary(Locale)` on `PlaceDescription`; leave `phrase()` untouched

## 4. Wire the listing page to use `summary(Locale)`

- [x] 4.1 Test: `LocationCacheController`'s model uses `summary(LocaleContextHolder.getLocale())` per entry rather than `phrase()` (verify: controller test asserting the model's display text, or template-rendering assertion if that's how the existing `LocationCacheControllerTest` is structured) — tests set `LocaleContextHolder` explicitly and reset it in `@AfterEach`; added an English case, a Polish case (the spec's "same entry, different UI language" scenario), and a no-country-code case
- [x] 4.2 Update `LocationCacheController` to build each entry's display text via `summary(LocaleContextHolder.getLocale())` — model-shape judgment call: the `entries` attribute is now `Map<CachedCoordinate, String>` (pre-rendered summaries) rather than `Map<CachedCoordinate, PlaceDescription>`. A parallel second map was the alternative; one map of the text the page actually shows keeps the template free of locale plumbing and leaves nothing else in the model unused, and the page displays nothing else from `PlaceDescription`.
- [x] 4.3 Update `templates/location-cache.html` to render the new summary text instead of `entry.value.phrase()`

## 5. Finalize

- [x] 5.1 Full `mvnw test` green — `mvnw.cmd -B verify`: 299 tests, 0 failures
- [x] 5.2 Live-verify: run the app, confirm `/location-cache` shows "suburb, locality, country" in English, then switch to `?lang=pl` and confirm the same entries now show the Polish country name without needing a fresh lookup — verified by curl against a locally-run instance. Judgment call: the real `data/generated/location-cache.json` has no stored country codes yet (every entry predates this change), so a live check against it could only ever show the degraded no-country form; the app was instead run on port 8099 with `-Dspring-boot.run.workingDirectory` pointing at a scratch directory holding a three-entry synthetic cache (two with country codes, one without) — the real `data/` directory was neither read nor modified. Result: English "Dębniki, Kraków, Poland" / "Woluwe-Saint-Pierre, Brussels, Belgium", `?lang=pl` "Dębniki, Kraków, Polska" / "Woluwe-Saint-Pierre, Brussels, Belgia" for the same entries with no lookup in between, and the country-less entry rendering as "Caniço de Baixo, Caniço".
- [x] 5.3 `openspec validate show-cached-location-country --strict` passes
