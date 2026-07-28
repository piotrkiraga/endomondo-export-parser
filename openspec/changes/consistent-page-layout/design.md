## Context

`fragments/header.html`'s shared fragment already renders every page's `<h2>` title and the `<section class="my-5"><div class="container"><div class="row"><div class="col-md-10 mx-auto">` spacing wrapper identically — verified by reading every template. The gaps found by that audit: three pages have no caption paragraph at all (`migration/review.html`, `migration/review-summary.html`, `error.html`); the three pages that *do* have a caption today (`migration/photo-report.html`, `migration/workout-report.html`, `migration/strava-dictionary.html`) render it as a plain `<p>`, visibly smaller than `home.html`/`upload.html`'s `<p class="lead">` — a second-order inconsistency only visible once captions exist everywhere to compare; and `migration/review-summary.html` renders its six-row uploaded/manual/skipped/failed/remaining/total summary as a raw Bootstrap `<table>` while `upload.html` and `migration/review.html` already render equivalent label-value data through the shared `.detail-list`/`.detail-row` component (`static/css/app.css`). The standalone `WorkoutReportGenerator` report (self-contained, no access to `static/css/app.css`) renders its own Sport/Start time/Distance/Duration facts as one plain `&mdash;`-joined text line in a `.meta` div — a third, different presentation for the same *kind* of data.

## Goals / Non-Goals

**Goals:**
- Every page has a one-sentence caption below its title, in the same place and same size — including the pages that already had one before this change.
- Every place that shows label-value data uses `.detail-list`/`.detail-row` (or, for the standalone report, its inline CSS equivalent) — no more raw `<table>` for that shape of data.

**Non-Goals:**
- `migration/strava-dictionary.html`'s gear table stays a `<table>` — it's a genuine list of records (name + id per item), not label-value facts about one thing; forcing it into `.detail-list` would be a worse fit for that data's actual shape. It's also slated for retirement by the separately-queued `enriching-strava-data-view` change.
- No new visual design system, no new component beyond `.detail-list` which already exists.
- No change to the shared header/title/spacing structure — already consistent.

## Decisions

- **Captions are added via new message keys, following the exact pattern already used** (`migration.photoReport.description` etc.) — `migration.review.description`, `migration.review.summary.description`, `error.description`, each a `<p class="lead">` placed right after the header, before the strava-status/content, mirroring where `home.html`/`upload.html` already place theirs.
- **`.lead` becomes the one caption style, applied retroactively to the three pages that already had a plain-`<p>` caption** (`migration/photo-report.html`, `migration/workout-report.html`, `migration/strava-dictionary.html`) — no text changes to those three, purely adding the class, so every caption in the app renders at the same size once this ships.
- **`migration/review-summary.html`'s table becomes a `.detail-list`** with one row per stat (uploaded/manual/skipped/failed/remaining/total) — same six values, same order, just the shared component instead of a bespoke `<table class="table w-auto">`.
- **`WorkoutReportGenerator` gets its own small CSS clone of `.detail-list`/`.detail-row` inside `ReportStylesUtil`**, not a shared stylesheet link — this file must stay self-contained and openable from disk with no dependency on the running app's assets, the same constraint that's already why `ReportStylesUtil` duplicates card/palette/dark-mode styling instead of linking `static/css/app.css` directly. The clone only needs to visually match, not literally share a CSS file.
- **What counts as "label-value data" vs. a genuine table**: one entity's several named facts (a workout's sport/start/distance/duration; a summary's uploaded/manual/skipped counts) → `.detail-list`. A list of *multiple* records of the same shape (gear items, ledger rows) → a real `<table>`. This distinction is why the dictionary page's gear table is explicitly out of scope, not an oversight.

## Risks / Trade-offs

- [Two near-identical CSS rule sets: `.detail-list` in `static/css/app.css` and its clone in `ReportStylesUtil`] → accepted, matches the existing precedent (the report's whole stylesheet is already a deliberate duplication for the same self-containment reason); a comment in `ReportStylesUtil` will point at `.detail-list` as the source of truth to keep them from drifting apart silently.
- [Caption text quality] → each caption is one plain sentence describing the page's purpose, reviewed for accuracy against what the page actually does, not filler text.

## Migration Plan

Purely additive/refactoring: new message keys, template markup changes, one new CSS rule set. No data, route, or controller logic changes; nothing to roll back beyond a code revert.

## Open Questions

None blocking.
