# web-ui-presentation Specification

## Purpose
TBD - created by archiving change refresh-ui-bootstrap. Update Purpose after archive.
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

### Requirement: The UI follows the browser color scheme
Pages SHALL render in light or dark mode according to the browser's `prefers-color-scheme`, using Bootstrap 5.3 color modes, degrading to light mode when JavaScript is unavailable.

#### Scenario: Dark preference yields dark mode
- **WHEN** a page is opened in a browser preferring dark color scheme
- **THEN** the page renders with Bootstrap's dark color mode active

### Requirement: The refresh changes presentation only
The Bootstrap upgrade SHALL NOT change any behavior or user-facing text: the upload flow, validation and error messages, the workout summary content, localization, and security rules all behave identically.

#### Scenario: Existing test suite passes unmodified
- **WHEN** the full test suite runs after the UI refresh
- **THEN** all tests pass without any modification to their assertions

