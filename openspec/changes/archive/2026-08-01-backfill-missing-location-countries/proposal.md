## Why

Every one of the real archive's 39 cached locations shows no country on the new listing page, because `OsmPlaceLookup` returns immediately on any cache hit — an already-cached coordinate is never re-resolved, even after country tracking was added. Since this archive's migration is already fully done, none of those coordinates will naturally get looked up again through normal use, so the gap won't close on its own; it needs an explicit one-time backfill.

## What Changes

- New "Fill in missing countries" action on the location cache listing page: an explicit, user-triggered POST that re-resolves only the cache entries currently missing a country code, via Nominatim (throttled the same way every other Nominatim call in this app already is), and patches just the country onto each — locality/suburb/nearby feature are left exactly as already cached.
- Entries that already have a country are untouched and not re-requested. A failed re-lookup for a given entry degrades silently (that entry stays without a country, same as today) rather than aborting the whole batch.
- **Non-goal**: this is not automatic and does not run on page load — the listing page's own "no live lookups" behavior (see the existing `location-cache-listing` requirement) is unchanged; the backfill is a distinct, explicit action, same pattern as the Home dashboard's existing "Refresh from Strava" button.

## Capabilities

### Modified Capabilities
- `location-cache-listing`: adds an explicit backfill action for entries missing a country.

## Impact

- New `LocationCacheCountryBackfillService` (or similar name), using the existing `NominatimClient`/`LocationCache`.
- `LocationCacheController`: new `POST /location-cache/backfill-countries` handler.
- `templates/location-cache.html`: new button/form, plus `fragments/messages-errors` wiring for the result message.
- i18n: new keys for the button label and the result message.
