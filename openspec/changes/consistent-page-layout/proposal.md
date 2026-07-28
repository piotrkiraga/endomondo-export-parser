## Why

A page-by-page audit found the title and page-wrapper spacing are already consistent everywhere (the shared `fragments/header` renders the same `<h2>` title and container/spacing structure on every page) — but real gaps remain: some pages have no descriptive caption explaining what they're for, the captions that do exist aren't styled consistently with each other, and one page renders its key-value summary data as a raw Bootstrap `<table>` instead of the `.detail-list`/`.detail-row` component two other pages already share. These are inconsistencies a user notices while clicking around, not just a developer's nitpick.

## What Changes

- Every page gains a one-sentence descriptive caption below its title, matching the pattern `home.html`/`upload.html` (added in a prior change) already use. Missing today: `migration/review.html`, `migration/review-summary.html`, `error.html`.
- Every caption, new and existing, renders at the same size: Bootstrap's `.lead` class. `home.html`/`upload.html` already use it; `migration/photo-report.html`, `migration/workout-report.html`, and `migration/strava-dictionary.html` currently have a caption but not the class, rendering visibly smaller than the other two — this normalizes all of them.
- `migration/review-summary.html`'s raw `<table>` (uploaded/manual/skipped/failed/remaining/total counts) is replaced with the shared `.detail-list`/`.detail-row` component, matching how `upload.html`'s workout summary and `migration/review.html`'s workout details already render equivalent label-value data.
- The standalone, self-contained workout report (`WorkoutReportGenerator`) renders its per-workout Sport/Start time/Distance/Duration facts as a `.detail-list`-equivalent stacked list (a small CSS clone added to `ReportStylesUtil`, since this file can't link to the running app's `static/css/app.css`) instead of one dense `&mdash;`-joined text line, bringing it visually in line with the in-app pages that show the same kind of data. (`PhotoReportGenerator`'s `.meta` line has no comparable multi-field data — just a start time and a link — so it's unaffected.)

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `web-ui-presentation`: gains requirements that every page has a descriptive caption below its title, that every caption is styled consistently, and that label-value data (as opposed to a genuine multi-row/multi-column table) uses the shared `.detail-list` component everywhere it appears, including the standalone workout report.

## Non-goals

- `migration/strava-dictionary.html`'s gear table is explicitly out of scope: it's a genuine list of records (name + id per gear item), a shape `.detail-list` (a 2-column label/value pattern) doesn't fit — a real `<table>` is the right component for that data, not an inconsistency to fix. It's also slated for retirement by the separately-queued `enriching-strava-data-view` change, so touching its caption/table here would be redundant work.
- No change to the shared title/navigation/spacing structure itself (`fragments/header`) — already consistent, verified during the audit that produced this proposal.
- No visual redesign beyond what's needed for caption + `.detail-list` consistency — no new color palette, no new component beyond the one that already exists.

## Characterization vs. Change

- Preserved: every page's actual functionality, routes, and data are unchanged — this only affects a caption's presence and how existing data is visually structured.
- Preserved: `.detail-list`/`.detail-row` itself (`static/css/app.css`) is unchanged — reused, not redesigned.
- Changed: `migration/review.html`, `migration/review-summary.html`, `error.html` templates gain a caption; `migration/photo-report.html`, `migration/workout-report.html`, `migration/strava-dictionary.html` gain `class="lead"` on their existing caption; `migration/review-summary.html` gains a `.detail-list` instead of a `<table>`; `ReportStylesUtil.CSS` gains a `.detail-list`-equivalent rule set; `WorkoutReportGenerator` uses it for its per-workout facts.

## Impact

- `templates/migration/review.html`, `templates/migration/review-summary.html`, `templates/error.html` (new captions; the summary page's table becomes a detail-list)
- `templates/migration/photo-report.html`, `templates/migration/workout-report.html`, `templates/migration/strava-dictionary.html` (existing caption gains `class="lead"`, no text change)
- `messages.properties`, `messages_en.properties`, `messages_pl.properties` (new caption message keys)
- `util/ReportStylesUtil` (new CSS rules mirroring `.detail-list`/`.detail-row`)
- `service/WorkoutReportGenerator` (renders its per-workout facts via the new CSS instead of a plain text line)
- No changes to any controller logic, routing, or data computation
