## 1. Copy-path control

- [x] 1.1 Add a `copyPath(button)` JS function to `PhotoReportGenerator`'s existing inline `<script>` block: reads the absolute path from the triggering button's `data-path` attribute, tries `navigator.clipboard.writeText()`, falls back to a temporary off-screen `<textarea>` + `document.execCommand('copy')` when the Clipboard API is unavailable or rejects, and briefly flips the button's own text to a "copied" confirmation (verify: unit test asserts the generated HTML contains the function and both copy paths)
- [x] 1.2 Render a copy-path button beside each matched photo's `<figure>`, with `data-path` set to that photo's absolute local path (properly attribute-escaped, matching the existing `escape()` usage) (verify: unit test asserts the button and `data-path` are present per photo, with the correct absolute path)
- [x] 1.3 Render the same copy-path button for unmatched photos (verify: unit test covering the unmatched section)

## 2. Validate

- [x] 2.1 Full `mvnw test` green; generate the report against the real archive, open it both from disk and through the running app, and confirm the copy button works in both (verify: `openspec validate photo-report-copy-path --strict` passes)
