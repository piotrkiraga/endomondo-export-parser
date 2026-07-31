## Why

The location cache listing page (just added) shows one row per coordinate. Real archives cluster heavily around a handful of places — e.g. 15 of the 56 real cached coordinates all resolve to "Dębniki, Kraków" — so the page currently repeats the same place name over and over, making it hard to see at a glance which distinct places the cache actually knows about.

## What Changes

- The listing groups entries by resolved place (the same locale-aware `summary(Locale)` text used today) instead of listing one row per coordinate: one row per distinct place, showing how many coordinates map to it and listing them.
- Groups are ordered alphabetically by place name (a natural side effect of grouping by that key).
- No change to what's stored or how a place is resolved — this only reshapes how the existing data is displayed.

## Capabilities

### Modified Capabilities
- `location-cache-listing`: entries are grouped by distinct place instead of listed one row per coordinate.

## Impact

- `LocationCacheController`: replace the per-coordinate map with a per-place grouping.
- New `LocationGroup` model record (place + its coordinates).
- `CachedCoordinate` gains a small display-formatting method reused by the template.
- `templates/location-cache.html`: table restructured to place/count/coordinates columns.
- i18n: new keys for the count/coordinates column headers; the lead description text is reworded; the now-unused per-coordinate latitude/longitude column keys are removed.
