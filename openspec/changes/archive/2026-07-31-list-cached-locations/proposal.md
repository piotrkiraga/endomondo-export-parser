## Why

The Home dashboard's "Where things stand" card shows a bare count for "Cached locations" with no way to see what's actually in the `LocationCache` — useful for spot-checking place-lookup quality (e.g. after a batch of migrations) without opening `data/generated/location-cache.json` by hand.

## What Changes

- Add a new `/location-cache` page listing every entry currently in `LocationCache`: its coordinate (parsed back from the cache key) and the resolved `PlaceDescription` (via its existing `phrase()` helper).
- `LocationCache` gains a read-all method (`entries()`) alongside its existing `get`/`put`/`size` — no change to how entries are stored or keyed.
- The "Cached locations" stat on the Home dashboard becomes a link to `/location-cache` instead of plain text.
- **Non-goal, per the issue's own scoping note**: linking each cache entry back to the workout(s) that produced it. `LocationCache` has no stored reference to originating workouts, and adding one would mean changing what's persisted (a new field, a migration of the existing cache file) — out of scope here. The listing shows coordinate + resolved place only.

## Capabilities

### New Capabilities
- `location-cache-listing`: a page rendering every entry in `LocationCache` (coordinate + resolved place description), reachable from the Home dashboard.

### Modified Capabilities
- `app-status-dashboard`: the "Cached locations" stat becomes a link to the new listing page instead of a plain count.

## Impact

- `LocationCache` (service): new `entries()` method.
- New `LocationCacheController` (or similar) + `templates/location-cache.html`.
- `home.html`: wrap the "Cached locations" `detail-value` in a link.
- New i18n keys (`messages_en.properties` / `messages_pl.properties`) for the new page's title/headings, plus a `title.locationCache` entry.
- `PageRenderSmokeTest`: add the new route.
