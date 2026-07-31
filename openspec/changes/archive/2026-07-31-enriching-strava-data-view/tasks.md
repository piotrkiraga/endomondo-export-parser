## 1. LocationCache size accessor

- [x] 1.1 Add `LocationCache.size()`, reading the current on-disk cache under the same lock-and-reload pattern as `get`/`put` (verify: unit test asserts size reflects entries written by a prior `put`, and is 0 for an absent/empty cache file)

## 2. AppStatus model and resolver

- [x] 2.1 Add `model/AppStatus` record: archive workout count (nullable), ledger counts by `LedgerStatus` (done/failed/skipped/pending), location cache size (verify: `mvnw compile`)
- [x] 2.2 Add `service/AppStatusResolver`, constructor-injecting `ArchiveScanner`, `MigrationLedger`, `LocationCache`; one method producing an `AppStatus` from the archive root path (verify: unit tests against fixture archives / temp-dir-backed stores — covering archive present/absent, ledger empty/mixed-status, cache empty/populated, each independently)

## 3. Merge the Strava Dictionary page into Home

- [x] 3.1 `HomeController` gains a `StravaTokenStore` dependency and the `view()`/new `refresh()` logic moved verbatim from `StravaDictionaryController` (`stravaConnected`, `snapshot`, `refreshedAtDisplay` model attributes; `POST /home/refresh` calling `StravaDictionaryService.refresh()`) (verify: controller tests — ported from `StravaDictionaryControllerTest` — calling `view()`/`refresh()` directly against a rig, covering never-refreshed, successful refresh, and refresh-while-disconnected)
- [x] 3.2 `home.html` absorbs the dictionary page's athlete card, gear table, refresh button, and never-refreshed state (reusing the existing `migration.stravaDictionary.*` message keys unchanged); the existing small athlete greeting is removed (verify: live GET against a connected account, and against a fresh/disconnected install, screenshot both)
- [x] 3.3 Delete `StravaDictionaryController`, `templates/migration/strava-dictionary.html`, and `StravaDictionaryControllerTest` (superseded by the ported tests in 3.1); remove the "Strava data" entry from `fragments/header.html`; drop the now-unused `title.stravaDictionary` message key (verify: `mvnw test`; grep confirms no remaining reference to `/migration/strava-dictionary`)

## 4. Wire the new dashboard stats into the home page

- [x] 4.1 `HomeController` constructor-injects `AppStatusResolver` and the `endomondo.archive.root` property (same property `MigrationController` already reads); `view()` adds the resolved `AppStatus` to the model (verify: controller test asserting the model attribute is present)
- [x] 4.2 `home.html` gains a dashboard section rendering the archive count, ledger breakdown, and location cache size, with a localized "not yet available" fallback per stat when its value is absent — reusing the existing `.detail-list`/`.detail-row` component from `static/css/app.css` (verify: live GET against the real archive, and against a fresh/empty `data/` directory, screenshot both)

## 5. Validate

- [x] 5.1 Full `mvnw test` green; `openspec validate enriching-strava-data-view --strict` passes
