## MODIFIED Requirements

### Requirement: The location cache listing page shows every cached entry
The system SHALL provide a page listing every entry currently in the location cache: the coordinate the entry is keyed by, and a suburb/locality/country summary of the resolved place for that coordinate, with the country name shown in whichever UI language is currently active.

#### Scenario: Cache has entries
- **WHEN** the location cache listing page is requested and the location cache contains entries
- **THEN** the page lists each entry's coordinate and a "suburb, locality, country" summary of its resolved place

#### Scenario: Cache is empty or absent
- **WHEN** the location cache listing page is requested and the location cache has never been written, or has no entries
- **THEN** the page shows that there are no cached locations yet, rather than an error

#### Scenario: An entry has no stored country
- **WHEN** the location cache listing page is requested and an entry predates country tracking (no country code stored)
- **THEN** that entry's summary omits the country rather than showing an error or a placeholder

#### Scenario: The same entry is viewed in a different UI language
- **WHEN** the location cache listing page is requested with a different active UI language than a previous request, for an entry with a stored country code
- **THEN** the country name in that entry's summary is shown in the newly active language, without needing the entry to be re-resolved
