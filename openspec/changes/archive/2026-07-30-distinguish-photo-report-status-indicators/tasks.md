## 1. Styling

- [x] 1.1 In `ReportStylesUtil.CSS`, add `--migrated-bg`/`--migrated-accent` custom properties (light and dark theme variants, mirroring the existing `--uploaded-bg` pattern) and repoint `.workout.migrated` to a purple/indigo accent (`#6f42c1`) instead of the green shared with `.workout.uploaded`; `.workout.uploaded` and its `.mark-uploaded` button keep their original green (`--uploaded-bg`/`#2a9d5c`) unchanged (verify: `mvnw compile`) — **corrected 2026-07-30: initial implementation had this backwards (moved `.uploaded` to purple, left `.migrated` green); swapped to match, since `--uploaded-bg`'s green was `.uploaded`'s original color**

## 2. Verification

- [x] 2.1 Test: a generated photo report's CSS defines distinct color values for `.workout.migrated` and `.workout.uploaded` (verify: unit test asserting `ReportStylesUtil.CSS` contains the new migrated-accent value and it differs from the uploaded accent value)
- [x] 2.2 Full `mvnw test` green; `openspec validate distinguish-photo-report-status-indicators --strict` passes — both confirmed. **Deferred:** the live visual confirmation (regenerating the photo report against the real archive and eyeballing a migrated-and-marked/migrated-only/marked-only workout) requires a CSRF-protected form POST through the browser and real Strava calls; not done this session due to time — left for the user's own next look at the report.
