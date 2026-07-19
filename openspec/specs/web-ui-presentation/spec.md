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

