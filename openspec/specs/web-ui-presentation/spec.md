# web-ui-presentation Specification

## Purpose
Defines how the web user interface is presented: the Bootstrap styling shipped inside the executable jar, the shared page layout and navigation, and the light/dark color scheme with its user-controlled override. Behavior, wording, and localization of the application itself are specified by the other capabilities.

## Requirements
### Requirement: The UI ships a current Bootstrap 5 from the jar
The web UI SHALL be styled with a current Bootstrap 5 release (5.3 line or newer) packaged inside the executable jar (WebJar) and served from the application itself — no external CDN references in any template.

#### Scenario: Styling is self-contained
- **WHEN** the packaged jar runs without internet access and a page is requested
- **THEN** the Bootstrap CSS loads successfully from `/webjars/**` and no template references an external stylesheet or script host

### Requirement: Pages share a common layout with modern navigation
All pages SHALL share a single head fragment (one place defining the stylesheet link and page metadata) and a Bootstrap navbar containing the application navigation and the language switcher — replacing the plain link list.

#### Scenario: Navbar on every page
- **WHEN** the home, upload, or error page is rendered
- **THEN** it contains the shared navbar with Home and Upload links and the English/Polish language switcher, and its `<head>` comes from the shared fragment

### Requirement: The refresh changes presentation only
The Bootstrap upgrade SHALL NOT change any behavior or user-facing text: the upload flow, validation and error messages, the workout summary content, localization, and security rules all behave identically.

#### Scenario: Existing test suite passes unmodified
- **WHEN** the full test suite runs after the UI refresh
- **THEN** all tests pass without any modification to their assertions

### Requirement: The color scheme is automatic with a persistent user override
Pages SHALL render in light or dark mode resolved in this order: the user's stored explicit choice (per browser), else the browser's `prefers-color-scheme`, else light (including when JavaScript is unavailable). A moon/sun toggle in the navbar SHALL switch the mode instantly, persist the choice in the browser, and carry a localized accessible label. While no explicit choice is stored, live changes to the browser preference SHALL keep being followed.

#### Scenario: Dark preference yields dark mode by default
- **WHEN** a page is opened in a browser preferring dark color scheme and no explicit choice is stored
- **THEN** the page renders with Bootstrap's dark color mode active

#### Scenario: Toggle switches instantly and persists
- **WHEN** the user clicks the theme toggle and then navigates to another page or reloads
- **THEN** the mode switches without a page reload, and the chosen mode is active on the subsequent page regardless of the browser preference

#### Scenario: Toggle is localized for assistive technology
- **WHEN** the navbar renders in English or Polish
- **THEN** the toggle button carries an accessible label from the message bundles in that language

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

