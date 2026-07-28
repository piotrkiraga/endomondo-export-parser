## Why

Attaching photos to a migrated Strava activity is unavoidably manual — Strava's API has no endpoint to upload a photo at all, so this has been an explicit non-goal since the original migration proposal. The photo report already lists every photo grouped by workout with a "view on Strava" link, but getting a photo's file path from the report into Strava's own attach dialog today means locating the file yourself in a file browser. A one-click "copy path" button per photo, paired with the existing "view on Strava" link, tightens that loop: click copy, click the Strava link, paste the path straight into Strava's file-attach dialog.

## What Changes

- Each photo on the photo report gets its own "copy path" button, copying that photo's absolute local file path to the clipboard.
- Uses the browser's Clipboard API where available, with a fallback (a legacy copy command via a temporary selection) for contexts where the Clipboard API is unavailable — notably when the report is opened directly from disk (`file://`), which is its primary, explicitly-required mode of use.
- Scoped to the photo report only. The migration review page already has an analogous but different mechanism (a click-to-select readonly input per photo) that this does not change.

## Capabilities

### New Capabilities
- `photo-report-copy-path`: the per-photo copy-to-clipboard control on the photo report.

### Modified Capabilities
(none — this only adds a control to an existing report; it doesn't change what the report contains or how photos are matched/geotagged)

## Non-goals

- No automation of the actual Strava upload — still not possible, Strava's API has no photo-upload endpoint.
- No change to the migration review page's existing click-to-select path input.
- No change to which photos appear on the report, their geotagging, or their captions.

## Characterization vs. Change

- Preserved: photo grouping, captions, the lightbox, the "view on Strava" link, and the report's fully self-contained/offline nature are all unchanged.
- Changed: `PhotoReportGenerator` adds one button and a small inline script per the report's existing inline-`<script>` pattern (it already has one, for the lightbox).

## Impact

- `service/PhotoReportGenerator` (per-photo markup + one new inline JS function)
- No changes to `PhotoGeotagger`, `MigrationLedger`, or any Strava-facing code
