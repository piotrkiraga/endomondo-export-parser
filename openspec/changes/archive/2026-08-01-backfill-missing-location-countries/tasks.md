## 1. Backfill service

- [x] 1.1 Test: `LocationCacheCountryBackfillService.backfillMissingCountries()` updates only entries with a null `countryCode`, preserving locality/suburb/nearbyFeature and setting the new country code, using a fake/mocked `NominatimClient` — the project has no Mockito, so the fake is a `ScriptedNominatimClient extends NominatimClient` subclass, matching the existing `RecordingExecutor`/`CountingExecutor` test-fake style
- [x] 1.2 Test: entries that already have a country code are left untouched and no lookup is made for them (verify via a `NominatimClient` mock's invocation count) — the fake records every coordinate it was asked for, so the assertion is on that recorded list
- [x] 1.3 Test: a re-lookup that fails or returns no country code leaves that entry unchanged, and the method still processes the remaining entries and returns the correct count
- [x] 1.4 Test: an empty cache, or a cache where every entry already has a country, returns 0 and makes no lookups
- [x] 1.5 Implement `LocationCacheCountryBackfillService`

## 2. Controller and template

- [x] 2.1 Test: `POST /location-cache/backfill-countries` calls the service and adds an info message with the count to the model, then re-renders `location-cache` with a fresh `groups` model — added a second test for the "nothing to fill in" branch too, since the spec has a scenario for it
- [x] 2.2 Implement the controller handler
- [x] 2.3 Add the backfill form/button to `templates/location-cache.html`, and add `fragments/messages-errors` to the page (not previously present) — `fragments/messages-errors` turned out to be already included on this page, so only the form/button was added
- [x] 2.4 Add i18n keys (button label, "N countries filled in" / "nothing to fill in" messages) to `messages_en.properties`/`messages_pl.properties`

## 3. Finalize

- [x] 3.1 Full `mvnw test` green — 310 tests, 0 failures
- [x] 3.2 Live-verify against the real `data/generated/location-cache.json`: confirm the button fills in countries for the real 39 entries and the page then shows them (respect the ~1/sec throttle — this will take under a minute for the real archive) — the real cache had grown to 56 entries, all missing a country; one POST filled in all 56 in 55s (PL 26, BE 21, PT 4, CH 2, NL 2, IT 1), localities/suburbs/nearby features unchanged. Live rendering also exposed a placement constraint: with the table that long the response commits before the form is reached, and Spring Security can no longer open the session its CSRF token needs, so the form sits above the table rather than below it
- [x] 3.3 `openspec validate backfill-missing-location-countries --strict` passes
