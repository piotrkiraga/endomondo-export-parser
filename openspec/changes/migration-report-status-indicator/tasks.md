## 1. Shared styling

- [ ] 1.1 Add `.workout.migrated` to `ReportStylesUtil.CSS` (green accent border/background tint, mirroring the existing `.workout.skip` pattern) (verify: `mvnw compile`; visual check via a generated report)

## 2. Workout report

- [ ] 2.1 `WorkoutReportGenerator.activityLink()` (or its caller) returns whether the workout is migrated alongside the existing link/text, and the per-workout `<section class="workout ...">` gains the `migrated` class plus the checkmark/open-circle icon beside the link text, when applicable (verify: unit test asserting the class and icon appear only when an activity id is present)

## 3. Photo report

- [ ] 3.1 Apply the identical `migrated` class + icon treatment in `PhotoReportGenerator`'s per-workout section, reusing the same class name and icons as the workout report (verify: unit test asserting the class and icon appear only when an activity id is present)

## 4. Validate

- [ ] 4.1 Full `mvnw test` green; generate both reports against the real archive and confirm visually that migrated/not-migrated cards are clearly distinguished (verify: `openspec validate migration-report-status-indicator --strict` passes)
