# export-data-management Specification

## Purpose
TBD - created by archiving change secure-export-data. Update Purpose after archive.
## Requirements
### Requirement: Personal export data lives outside the source tree
Personal Endomondo/Strava export archives (TCX, workout JSON, profile HTML, weight data) MUST NOT reside under `src/`. They SHALL live in the git-ignored `data/` directory at the repository root, which is excluded from packaging and resource filtering.

#### Scenario: Repository contains no personal data under src
- **WHEN** the repository working tree is inspected
- **THEN** no files derived from a personal Endomondo/Strava export exist under `src/main/resources/`

#### Scenario: Local archive available for manual experimentation
- **WHEN** a developer places an export archive under `data/`
- **THEN** `git status` reports no untracked or modified files for that content

### Requirement: Version control rejects export archives
The repository's `.gitignore` SHALL exclude the `data/` directory and common export archive patterns so personal export data cannot be committed accidentally.

#### Scenario: Export data is ignored by git
- **WHEN** files are added under `data/`
- **THEN** `git check-ignore data/<any-file>` confirms they are ignored

### Requirement: Anonymized fixtures for testing
The project SHALL provide small anonymized sample export files under `src/test/resources/fixtures/` — at least one Endomondo workout JSON file and one TCX file — containing no real names, profile identifiers, or personally identifying metadata, while remaining structurally faithful to the frozen Endomondo export format.

#### Scenario: Fixtures exist and are committed
- **WHEN** the test classpath is inspected
- **THEN** `src/test/resources/fixtures/` contains at least one workout JSON and one TCX sample tracked by git

#### Scenario: Fixtures contain no personal data
- **WHEN** fixture contents are reviewed
- **THEN** they contain only fabricated or scrubbed names, IDs, and coordinates

