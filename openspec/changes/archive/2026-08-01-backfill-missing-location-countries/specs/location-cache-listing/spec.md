## ADDED Requirements

### Requirement: Missing countries can be backfilled on demand
The location cache listing page SHALL offer an explicit action that re-resolves the country for every currently cached entry that has no stored country code, leaving every other stored field (and every entry that already has a country) unchanged.

#### Scenario: Backfill finds entries missing a country
- **WHEN** the backfill action is triggered and one or more cached entries have no country code
- **THEN** each such entry is re-resolved and, where a country is found, updated in place with that country code, and the page reports how many entries were updated

#### Scenario: Nothing to backfill
- **WHEN** the backfill action is triggered and every cached entry already has a country code
- **THEN** no re-lookups are made and the page reports that there was nothing to fill in

#### Scenario: A re-lookup fails for one entry
- **WHEN** the backfill action is triggered and the re-lookup for a specific entry fails or still returns no country
- **THEN** that entry is left as it was (still without a country) and the backfill continues with the remaining entries rather than aborting

#### Scenario: The listing page itself still makes no lookups on load
- **WHEN** the location cache listing page is requested normally (not via the backfill action)
- **THEN** rendering it still makes no reverse-geocoding lookup, exactly as before this change
