## 1. Captions: add the missing ones, normalize the rest

- [ ] 1.1 Add `migration.review.description`, `migration.review.summary.description`, `error.description` message keys (English + Polish + base `messages.properties`, matching the pattern every other caption key already follows) (verify: `mvnw compile`)
- [ ] 1.2 Add the caption `<p class="lead">` to `migration/review.html`, `migration/review-summary.html`, and `error.html`, positioned the same way `home.html`/`upload.html` already do it (verify: `LocalizationTest`-style assertion or a new test confirming the caption text renders; live GET on each page)
- [ ] 1.3 Add `class="lead"` to the existing caption `<p>` in `migration/photo-report.html`, `migration/workout-report.html`, and `migration/strava-dictionary.html` — no text change (verify: live GET on each page, visually confirm all captions across the app now render at the same size)

## 2. Migration review summary: table to detail-list

- [ ] 2.1 Replace `migration/review-summary.html`'s `<table class="table w-auto">` with a `.detail-list`/`.detail-row` block, same six stats in the same order (verify: existing controller test for the summary page still passes with updated assertions if it checks table markup; live GET)

## 3. Standalone workout report: detail-list-equivalent styling

- [ ] 3.1 Add a `.detail-list`/`.detail-row` CSS clone to `ReportStylesUtil.CSS`, with a comment pointing at `static/css/app.css`'s `.detail-list` as the source of truth (verify: `mvnw compile`)
- [ ] 3.2 `WorkoutReportGenerator` renders each workout's sport/start time/distance/duration through the new markup instead of the `&mdash;`-joined `.meta` line (verify: unit test asserting the new markup/classes appear per workout; generate the report against the real archive and visually confirm)

## 4. Validate

- [ ] 4.1 Full `mvnw test` green; `openspec validate consistent-page-layout --strict` passes
