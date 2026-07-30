## MODIFIED Requirements

### Requirement: Each workout can be marked as uploaded
The photo report SHALL render a "mark as uploaded" control on every workout group, which toggles that workout's marked state when activated. A marked workout group SHALL be visually distinguished from an unmarked one (e.g. a distinct style applied to the whole group), and that treatment SHALL remain visually distinguishable from the report's separate migrated-workout treatment (`migration-report-status-indicator`) even when both apply to the same workout group.

#### Scenario: Marking a workout applies a visual treatment
- **WHEN** the user activates the mark-as-uploaded control for a workout group
- **THEN** that workout group is shown with the marked visual treatment, distinguishing it from unmarked groups

#### Scenario: Marking is independent per workout
- **WHEN** one workout group is marked as uploaded
- **THEN** every other workout group's marked/unmarked state is unaffected

#### Scenario: Activating the control again unmarks the workout
- **WHEN** the user activates the mark-as-uploaded control for an already-marked workout group
- **THEN** that workout group returns to its unmarked visual state

#### Scenario: Marked treatment stays distinguishable on a migrated workout
- **WHEN** a workout group that already carries the migrated-workout treatment is also marked as uploaded
- **THEN** both the migrated treatment and the marked treatment remain individually recognizable, rather than rendering identically
