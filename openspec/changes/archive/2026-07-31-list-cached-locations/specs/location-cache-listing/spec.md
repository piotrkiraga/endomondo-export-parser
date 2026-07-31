## Purpose

Lets the user inspect what the location cache actually holds — every coordinate it has resolved and the place description it resolved to — without opening the underlying JSON file by hand.

## ADDED Requirements

### Requirement: The location cache listing page shows every cached entry
The system SHALL provide a page listing every entry currently in the location cache: the coordinate the entry is keyed by, and the resolved place description for that coordinate.

#### Scenario: Cache has entries
- **WHEN** the location cache listing page is requested and the location cache contains entries
- **THEN** the page lists each entry's coordinate and its resolved place description

#### Scenario: Cache is empty or absent
- **WHEN** the location cache listing page is requested and the location cache has never been written, or has no entries
- **THEN** the page shows that there are no cached locations yet, rather than an error

### Requirement: The listing page makes no live lookups
Rendering the location cache listing SHALL read only the local location cache — it SHALL NOT perform any live reverse-geocoding lookup or other network call.

#### Scenario: Listing renders offline
- **WHEN** the location cache listing page is requested without network access
- **THEN** the page still renders using only the locally cached entries
