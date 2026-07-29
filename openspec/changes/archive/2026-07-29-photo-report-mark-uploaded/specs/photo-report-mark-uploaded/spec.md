## Purpose

Lets a user track, per workout, which workouts' photos they have already manually attached on Strava, so a long photo list can be worked through across multiple sessions without losing track of progress.

## ADDED Requirements

### Requirement: Each workout can be marked as uploaded
The photo report SHALL render a "mark as uploaded" control on every workout group, which toggles that workout's marked state when activated. A marked workout group SHALL be visually distinguished from an unmarked one (e.g. a distinct style applied to the whole group).

#### Scenario: Marking a workout applies a visual treatment
- **WHEN** the user activates the mark-as-uploaded control for a workout group
- **THEN** that workout group is shown with the marked visual treatment, distinguishing it from unmarked groups

#### Scenario: Marking is independent per workout
- **WHEN** one workout group is marked as uploaded
- **THEN** every other workout group's marked/unmarked state is unaffected

#### Scenario: Activating the control again unmarks the workout
- **WHEN** the user activates the mark-as-uploaded control for an already-marked workout group
- **THEN** that workout group returns to its unmarked visual state

### Requirement: Marked state persists in the browser across sessions
The marked/unmarked state for each workout SHALL be stored in the browser's `localStorage`, keyed by the workout's basename, so it survives closing and reopening the report and regenerating the report file, as long as the same browser (and local storage) is used.

#### Scenario: Reopening the report preserves marks
- **WHEN** a workout is marked as uploaded, the report tab is closed, and the same report file is reopened in the same browser
- **THEN** that workout is still shown with the marked visual treatment

#### Scenario: Regenerating the report preserves marks
- **WHEN** a workout is marked as uploaded and the photo report is regenerated (overwriting the report file) and reopened in the same browser
- **THEN** that workout, identified by its basename, is still shown with the marked visual treatment

#### Scenario: A different browser or cleared storage starts unmarked
- **WHEN** the report is opened in a browser (or profile) that never marked a given workout, or after that browser's local storage for the report has been cleared
- **THEN** that workout shows the unmarked visual state
