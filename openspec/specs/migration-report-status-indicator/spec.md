# migration-report-status-indicator Specification

## Purpose
Gives both offline reports (workout report and photo report) a shared, at-a-glance visual signal — a card accent plus an icon — for whether a workout has actually migrated to Strava, distinct from the existing planned-action badges.

## Requirements

### Requirement: Migrated workouts are visually distinguished on both reports
Each workout card on the workout report and the photo report SHALL carry a distinct visual treatment (card border/background accent plus an icon beside the Strava link text) when that workout has a recorded Strava activity id, differing from the treatment of a workout with no recorded activity id. The same treatment SHALL be applied identically on both reports.

#### Scenario: Migrated workout stands out on the workout report
- **WHEN** the workout report renders a workout with a recorded activity id
- **THEN** its card carries the migrated accent and its Strava link is preceded by the migrated icon

#### Scenario: Migrated workout stands out on the photo report
- **WHEN** the photo report renders a workout with a recorded activity id
- **THEN** its card carries the same migrated accent and icon as the workout report uses

#### Scenario: Not-yet-migrated workout keeps the default card look
- **WHEN** either report renders a workout with no recorded activity id (pending, skipped, or failed alike)
- **THEN** its card does not carry the migrated accent, and "pending migration" is preceded by the not-migrated icon

### Requirement: The status indicator adds no external dependency
The icons and styling SHALL be plain inline text/CSS already embeddable in the existing self-contained report file — no icon font, external stylesheet, or CDN reference.

#### Scenario: Report still opens correctly offline
- **WHEN** a generated report file is opened directly from disk with no network access
- **THEN** the migrated/not-migrated styling and icons render identically to a browser opening it through the running app
