## MODIFIED Requirements

### Requirement: The color scheme is automatic with a persistent user override
Pages SHALL render in light or dark mode resolved in this order: the user's stored explicit choice (per browser), else the browser's `prefers-color-scheme`, else light (including when JavaScript is unavailable). A moon/sun toggle in the navbar SHALL switch the mode instantly, persist the choice in the browser, and carry a localized accessible label. While no explicit choice is stored, live changes to the browser preference SHALL keep being followed.

The standalone photo and workout reports SHALL follow the same resolution order — stored explicit choice, else `prefers-color-scheme`, else light — when opened through the running application, since they then share the browser origin the choice is stored under. When opened directly from disk, where the stored choice is not reachable, they SHALL fall back to `prefers-color-scheme`, else light, same as before this requirement's scope was extended to them.

#### Scenario: Dark preference yields dark mode by default
- **WHEN** a page is opened in a browser preferring dark color scheme and no explicit choice is stored
- **THEN** the page renders with Bootstrap's dark color mode active

#### Scenario: Toggle switches instantly and persists
- **WHEN** the user clicks the theme toggle and then navigates to another page or reloads
- **THEN** the mode switches without a page reload, and the chosen mode is active on the subsequent page regardless of the browser preference

#### Scenario: Toggle is localized for assistive technology
- **WHEN** the navbar renders in English or Polish
- **THEN** the toggle button carries an accessible label from the message bundles in that language

#### Scenario: A report served by the app follows the explicit choice
- **WHEN** the user has explicitly chosen a mode via the navbar toggle, and then opens the photo report or workout report through the running application (e.g. `/photo-report/index.html`)
- **THEN** the report renders in that explicitly chosen mode, even if the browser's `prefers-color-scheme` disagrees

#### Scenario: A report opened from disk falls back to system preference
- **WHEN** the generated report file is opened directly from disk (a `file://` URL), regardless of any explicit choice stored under the application's own origin
- **THEN** the report renders according to `prefers-color-scheme`, else light — the stored explicit choice is not reachable from that origin
