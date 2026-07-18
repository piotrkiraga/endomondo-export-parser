## Why

~1,400 files of real personal Endomondo/Strava export data (workout TCX files, profile HTML with personal details, weight history JSON) currently sit uncommitted under `src/main/resources/endomondo-strava-exports/`, one `git add` away from being permanently baked into history. Before any other brownfield work (tests, upgrades, refactoring) touches this repo, the personal data must be moved out of the source tree and replaced by small anonymized fixtures the test suite can rely on.

## What Changes

- Move the personal export archive out of `src/main/resources/` to a local, git-ignored data directory outside the packaged classpath.
- Add `.gitignore` rules so export archives can never be committed accidentally.
- Create a small set of anonymized sample files (1–2 workout JSON, 1–2 TCX) under `src/test/resources/fixtures/` for use by future characterization tests.
- Commit the currently dirty working tree (`pom.xml` modification) so the repo reaches a clean, known baseline state.

## Capabilities

### New Capabilities
- `export-data-management`: rules for where personal export data may live, what must be git-ignored, and what anonymized fixtures the project provides for testing.

### Modified Capabilities

(none — no existing specs yet; existing runtime behavior is unchanged)

## Non-goals

- No parsing logic changes, bug fixes, or refactoring (later change: parser rewrite).
- No test authoring beyond placing fixture files (later change: characterization tests).
- No dependency or platform upgrades.

## Preserved vs. changed behavior

- **Preserved**: all runtime behavior of the application. The moved directory is not referenced by any code, only by manual experimentation, so packaging output shrinks but behavior is identical.
- **Changed**: repository layout and hygiene only.

## Impact

- Filesystem: `src/main/resources/endomondo-strava-exports/` relocated to `data/` (git-ignored) at repo root.
- Git: new `.gitignore` entries; one baseline commit including the pending `pom.xml` change.
- Build: war/classpath no longer bloated by ~1,400 resource files (resource filtering also stops touching them).
- No code, API, or dependency changes.
