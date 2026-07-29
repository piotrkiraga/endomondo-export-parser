## MODIFIED Requirements

### Requirement: The report enables one-step manual photo attachment
For each migrated activity with photos, the report SHALL show the photos (rendered from their local archive paths) directly beside a hyperlink to the created Strava activity (`https://www.strava.com/activities/{id}`), which SHALL open in a new browser tab so the report itself remains open at the user's place in the list, plus the workout name and date. Before migration (dry-run), the activity link column SHALL show "pending migration".

#### Scenario: Post-migration report links photos to activities
- **WHEN** the report is generated after a migration run
- **THEN** each photo row pairs the image with the clickable Strava activity link for its workout

#### Scenario: Activity link opens without navigating away from the report
- **WHEN** the user clicks a "view on Strava" link
- **THEN** the Strava activity page opens in a new browser tab and the report remains open in its original tab, unnavigated
