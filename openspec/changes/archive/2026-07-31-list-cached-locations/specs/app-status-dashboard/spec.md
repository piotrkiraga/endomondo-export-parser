## MODIFIED Requirements

### Requirement: The home page shows the location cache's size
The home page SHALL show the number of distinct coordinates the location cache currently holds, as a link to the location cache listing page.

#### Scenario: Cache has cached lookups
- **WHEN** the home page is requested and the location cache file contains entries
- **THEN** the page shows their count, linked to the location cache listing page

#### Scenario: Cache is empty or absent
- **WHEN** the home page is requested and the location cache has never been written
- **THEN** the page shows the count as zero, not an error, still linked to the location cache listing page
