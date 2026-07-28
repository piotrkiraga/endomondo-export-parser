## ADDED Requirements

### Requirement: Every page has a descriptive caption below its title, styled consistently
Every page SHALL show a one-sentence caption describing its purpose immediately below the shared title, in the same position and the same size (Bootstrap's `.lead` style) across every page.

#### Scenario: Pages without a prior caption now have one
- **WHEN** the migration review page, the migration review summary page, or the error page is rendered
- **THEN** each shows a `.lead`-styled caption describing that page's purpose, in the same position as `home.html`/`upload.html`'s existing captions

#### Scenario: Existing captions are restyled, not reworded
- **WHEN** the photo report, workout report, or Strava dictionary trigger pages are rendered
- **THEN** their existing caption text is unchanged but now renders with the same `.lead` size as every other page's caption

### Requirement: Label-value data uses the shared detail-list component everywhere
Wherever a page shows several named facts about one thing (as opposed to a list of multiple same-shaped records), it SHALL use the shared `.detail-list`/`.detail-row` presentation rather than a bespoke table, including in the standalone workout report which cannot link to the running application's stylesheet.

#### Scenario: Migration review summary uses detail-list
- **WHEN** the migration review summary page renders its uploaded/manual/skipped/failed/remaining/total counts
- **THEN** they render through the `.detail-list`/`.detail-row` component, not a `<table>`

#### Scenario: The standalone workout report visually matches
- **WHEN** the generated workout report renders a workout's sport/start time/distance/duration
- **THEN** they render as a stacked label-value list matching `.detail-list`'s visual appearance, not one `&mdash;`-joined line

#### Scenario: Genuine record tables are unaffected
- **WHEN** a page shows a list of multiple same-shaped records (e.g. the Strava gear dictionary's name/id rows)
- **THEN** it continues to use a real table, not `.detail-list`
