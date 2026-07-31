## MODIFIED Requirements

### Requirement: The location cache listing page shows every cached entry
The system SHALL provide a page listing every distinct place currently resolved in the location cache, grouped by a suburb/locality/country summary of that place (with the country name shown in whichever UI language is currently active): each group SHALL show the place summary, how many cached coordinates map to it, and the list of those coordinates.

#### Scenario: Cache has entries
- **WHEN** the location cache listing page is requested and the location cache contains entries
- **THEN** the page lists each distinct resolved place with its coordinate count and the coordinates that map to it

#### Scenario: Multiple coordinates resolve to the same place
- **WHEN** several cached coordinates resolve to the same suburb/locality/country summary
- **THEN** they appear together under one row for that place, not as separate repeated rows

#### Scenario: Cache is empty or absent
- **WHEN** the location cache listing page is requested and the location cache has never been written, or has no entries
- **THEN** the page shows that there are no cached locations yet, rather than an error

#### Scenario: An entry has no stored country
- **WHEN** the location cache listing page is requested and an entry predates country tracking (no country code stored)
- **THEN** that entry's place summary omits the country rather than showing an error or a placeholder

#### Scenario: The same entry is viewed in a different UI language
- **WHEN** the location cache listing page is requested with a different active UI language than a previous request, for an entry with a stored country code
- **THEN** the country name in that entry's place summary is shown in the newly active language, without needing the entry to be re-resolved

### Requirement: The listing page makes no live lookups
Rendering the location cache listing SHALL read only the local location cache — it SHALL NOT perform any live reverse-geocoding lookup or other network call.

#### Scenario: Listing renders offline
- **WHEN** the location cache listing page is requested without network access
- **THEN** the page still renders using only the locally cached entries
