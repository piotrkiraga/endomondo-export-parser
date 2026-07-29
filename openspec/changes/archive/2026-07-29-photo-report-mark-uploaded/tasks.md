## 1. Mark-as-uploaded control

- [x] 1.1 Add a `data-basename` attribute (attribute-escaped via the existing `escape()` helper) to each workout's `<section class="workout">` element (verify: unit test asserts `data-basename` is present per workout group and matches its basename)
- [x] 1.2 Add `toggleUploaded(basename, button)` and an on-load init function to `PhotoReportGenerator`'s existing inline `<script>` block: the init function reads the single `localStorage` JSON object (keyed by basename) on load and applies the "uploaded" CSS class to every matching `<section>`; `toggleUploaded` flips the entry for one basename, re-persists the JSON object, and toggles the class on `button.closest('.workout')` (verify: unit test asserts the generated HTML contains both functions and the `localStorage` key name)
- [x] 1.3 Render a "mark as uploaded" control on each workout group, wired to call `toggleUploaded` with that workout's basename (verify: unit test asserts the control is present once per workout group)
- [x] 1.4 Add a `.workout.uploaded` rule to `ReportStylesUtil.CSS` giving marked workout groups a distinct visual treatment (verify: unit test asserts the generated report's embedded stylesheet contains the `.workout.uploaded` rule)

## 2. Strava link opens in a new tab

- [x] 2.1 Add `target="_blank" rel="noopener"` to the "view on Strava" link built in `activityLink` (verify: unit test asserts the generated Strava link carries both attributes)

## 3. Validate

- [x] 3.1 Full `mvnw test` green; generate the report against the real archive and confirm live: marking a workout applies the visual treatment, reloading the report (same browser) preserves the mark, regenerating the report and reopening it still preserves the mark, and the "view on Strava" link opens in a new tab leaving the report in place (verify: `openspec validate photo-report-mark-uploaded --strict` passes)
