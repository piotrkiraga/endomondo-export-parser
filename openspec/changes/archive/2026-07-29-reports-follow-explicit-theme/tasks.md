## 1. CSS attribute-selector overrides

- [x] 1.1 Add `:root[data-theme="light"]{...}` and `:root[data-theme="dark"]{...}` rules to `ReportStylesUtil.CSS`, mirroring the existing `:root{...}` and `@media (prefers-color-scheme:dark){:root{...}}` variable values exactly (verify: unit test asserts the generated report's embedded stylesheet contains both attribute-selector rules with the matching variable values)

## 2. Theme-read script

- [x] 2.1 Add a small `<head>`-placed script to `PhotoReportGenerator`'s `render()`, before `<body>`, that reads `localStorage.getItem('theme')` (wrapped in `try`/`catch`, silently doing nothing on failure or an unrecognized value) and sets `data-theme` on `<html>` when the value is `'light'` or `'dark'` (verify: unit test asserts the generated HTML contains the script in `<head>`, before `<body>`)
- [x] 2.2 Add the same script to `WorkoutReportGenerator`'s `render()` (its first `<script>` block) (verify: unit test asserts the generated HTML contains the script in `<head>`, before `<body>`)

## 3. Validate

- [x] 3.1 Full `mvnw test` green; with the app running, set the navbar theme explicitly to light while the OS/browser reports a dark `prefers-color-scheme`, generate and open both reports through the app, and confirm both render light; then open the same report file directly from disk and confirm it falls back to `prefers-color-scheme` (dark, in that scenario) (verify: `openspec validate reports-follow-explicit-theme --strict` passes)
