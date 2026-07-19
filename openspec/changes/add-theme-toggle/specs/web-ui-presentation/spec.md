# web-ui-presentation

## REMOVED Requirements

### Requirement: The UI follows the browser color scheme
**Reason**: Superseded by the user-controllable color scheme below — automatic preference-following remains the default behavior, but is no longer the whole requirement.
**Migration**: The automatic behavior is preserved verbatim as the no-stored-choice path of the new requirement; no template or configuration migration is needed.

## ADDED Requirements

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
