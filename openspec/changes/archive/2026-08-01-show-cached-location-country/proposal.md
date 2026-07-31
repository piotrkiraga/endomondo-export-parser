## Why

The location cache listing page (just added, still unshipped) shows each entry as `PlaceDescription.phrase()` — e.g. "in Caniço de Baixo, Caniço" — which reuses the same feature/suburb/locality phrasing built for workout names and photo captions. On the listing page specifically, entries that share a locality end up looking near-identical and give no sense of where in the world they are. The user wants a distinct, at-a-glance display for this page: suburb, city, and country (e.g. "Woluwe-Saint-Pierre, Brussels, Belgium", "Dębniki, Kraków, Polska"), with the country name matching whichever UI language (English/Polish) is active — not baked into the cache in one fixed language.

## What Changes

- `NominatimClient` additionally captures the ISO 3166-1 alpha-2 country code Nominatim already returns per lookup (`address.country_code`); `Locality` gains a `countryCode` field.
- `PlaceDescription` gains a `countryCode` field (nullable — see Non-goals) and a new `summary(Locale)` method: "suburb, locality, country", with the country name resolved from the code via the JDK's own locale data (`Locale.getDisplayCountry`) at render time — not stored pre-translated, so the same cached entry reads correctly in either UI language. `phrase()` (used for workout naming, descriptions, and photo captions) is unchanged.
- The location cache listing page uses `summary(Locale)` instead of `phrase()`.
- **Non-goal**: backfilling the ~56 entries already in `data/generated/location-cache.json` from before this change — they have no stored country code. `summary()` degrades gracefully (omits the country) for those until they're naturally re-resolved. No forced re-lookup pass.
- **Non-goal**: changing `phrase()`'s output or its call sites (workout naming, workout description, photo captions) — this only affects the new listing page's own display.

## Capabilities

### Modified Capabilities
- `location-cache-listing`: entries are now shown as "suburb, locality, country" (locale-aware), not the feature-oriented `phrase()` text.

## Impact

- `NominatimClient`, `Locality`, `PlaceDescription`, `OsmPlaceLookup` (main).
- `LocationCacheController` / `templates/location-cache.html` (use `summary()` instead of `phrase()`).
- Every existing `PlaceDescription` construction site (9 files, mostly tests) gains a 4th constructor argument.
