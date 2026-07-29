## ADDED Requirements

### Requirement: Each photo has a copy-path control
The photo report SHALL render a "copy path" control beside every photo (matched and unmatched alike), which copies that photo's absolute local file path to the clipboard when activated.

#### Scenario: Copying a matched photo's path
- **WHEN** the user activates the copy-path control for a photo grouped under a workout
- **THEN** that photo's absolute local file path is placed on the clipboard

#### Scenario: Copying an unmatched photo's path
- **WHEN** the user activates the copy-path control for a photo in the unmatched section
- **THEN** that photo's absolute local file path is placed on the clipboard

#### Scenario: Visual confirmation
- **WHEN** a copy-path control is activated
- **THEN** the control shows a brief inline confirmation that the copy happened, without a browser alert or navigating away

### Requirement: Copying works whether the report is opened from disk or served by the app
The copy-path control SHALL function correctly both when the report file is opened directly from disk and when it's served through the application's own resource mapping, falling back to a non-Clipboard-API copy mechanism when the Clipboard API is unavailable.

#### Scenario: Report opened directly from disk
- **WHEN** the generated report file is opened directly from disk (a `file://` URL) and a copy-path control is activated
- **THEN** the path is still copied to the clipboard, whether via the Clipboard API or its fallback

#### Scenario: Report served through the running app
- **WHEN** the report is viewed through the app's own `/photo-report/**` mapping and a copy-path control is activated
- **THEN** the path is copied to the clipboard the same way as when opened from disk
