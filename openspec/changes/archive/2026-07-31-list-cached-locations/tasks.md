## 1. LocationCache: read-all method

- [x] 1.1 Test: `LocationCache.entries()` returns every stored coordinate → `PlaceDescription` pair, unmodifiable, reflecting entries written via `put` (verify: unit test using the package-private temp-dir constructor, mirroring `LocationCacheTest`'s existing style) — split into `entriesExposeEveryStoredCoordinate` and `entriesCannotBeModified`
- [x] 1.2 Test: `entries()` on an empty/absent cache file returns an empty map, not an error (verify: unit test)
- [x] 1.3 Implement `entries()` on `LocationCache`, guarded by the same lock as `get`/`put`, returning `Map.copyOf(load())`

## 2. Location cache listing page

- [x] 2.1 Test: `LocationCacheController`'s `GET /location-cache` renders with entries present and with an empty cache, listing latitude/longitude/place phrase per entry (verify: `@WebMvcTest` or equivalent controller test) — `LocationCacheControllerTest`, plain `ModelAndView` inspection against `MigrationTestSupport`'s mocked-network rig (same reasoning as `HomeControllerTest`: no `@SpringBootTest`, so the real `data/` directory is never read); real template rendering is covered by `PageRenderSmokeTest`
- [x] 2.2 Implement `LocationCacheController` (`GET /location-cache`): parse each `entries()` key back into lat/lon, sort by coordinate string ascending, add to model — model attribute is a `LinkedHashMap<CachedCoordinate, PlaceDescription>` (new `model/CachedCoordinate` record holding the two parsed doubles), keeping the template's `entry.key`/`entry.value` shape
- [x] 2.3 Implement `templates/location-cache.html`: standard page scaffold, `.table.table-sm` with latitude/longitude/place columns (`entry.value.phrase()`), an empty-state message when there are no entries — table sits in a card whose `card-title` carries `locationCache.heading`, mirroring home.html's gear card
- [x] 2.4 Add i18n keys to `messages_en.properties`/`messages_pl.properties`: `title.locationCache`, `locationCache.heading`, `locationCache.description`, `locationCache.latitude`, `locationCache.longitude`, `locationCache.place`, `locationCache.empty`
- [x] 2.5 Add `/location-cache` to `PageRenderSmokeTest`'s route list

## 3. Wire up the Home dashboard link

- [x] 3.1 `home.html`: wrap the "Cached locations" `detail-value` in `<a th:href="@{/location-cache}">`
- [x] 3.2 Full `mvnw test` green; live-verify: run the app, confirm `/location-cache` renders real cached entries from `data/generated/location-cache.json`, and the Home page link navigates there — 288 tests green; live GET renders all 56 real cached entries (e.g. `32.646 / -16.835 / in Caniço de Baixo, Caniço`), Home shows `<a href="/location-cache">56</a>`, Polish version renders correctly. `locationCache.heading` was reworded to "Resolved coordinates" / "Rozpoznane współrzędne" so the card title doesn't repeat the page title verbatim

## 4. Finalize

- [x] 4.1 `openspec validate list-cached-locations --strict` passes
