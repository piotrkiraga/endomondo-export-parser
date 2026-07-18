## Context

A full personal Endomondo export (~1,400 files: TCX workouts, profile HTML, weights JSON, plus two duplicate copies under `_\Nowy folder*`) sits uncommitted in `src/main/resources/endomondo-strava-exports/`. Nothing in the code references this path (verified by grep), so it is purely local experimentation data — but it inflates the packaged war, passes through Maven resource filtering, and risks being committed. The `pom.xml` also has an uncommitted modification, so the repo has no clean baseline.

## Goals / Non-Goals

**Goals:**
- Zero personal data under `src/`; impossible to commit it accidentally.
- Committed, anonymized fixtures that later characterization tests can build on.
- A clean git baseline commit to anchor subsequent changes.

**Non-Goals:**
- Parser fixes, tests, upgrades (separate changes).
- Tooling to anonymize arbitrary exports — fixtures are hand-made once.

## Decisions

- **Destination `data/` at repo root, git-ignored** — keeps the archive next to the project for experimentation without being on the classpath. Alternative (moving it outside the repo entirely) rejected: less convenient, and gitignore makes in-repo storage safe.
- **Deduplicate on move** — `_\Nowy folder` and `_\Nowy folder (2)` are byte-copies of `Workouts/`; keep only the canonical `endomondo-2020-11-01/` structure and delete the duplicates.
- **Fixtures are scrubbed copies, not synthetic** — take one small real workout JSON and one TCX, then replace names/IDs and shift coordinates. Preserving the real (frozen) format quirks is the whole value; fully synthetic files risk not matching the format the buggy parser actually sees.
- **Fixtures live in `src/test/resources/fixtures/`** — test-only classpath, never packaged in the war.

## Risks / Trade-offs

- [Scrubbing misses a personal field] → review each fixture line-by-line before committing; fixtures are small (single workout).
- [gitignore pattern too broad, hides wanted files] → ignore only `data/`, not generic `*.tcx`.

## Migration Plan

Pure local file moves plus one commit; nothing deployed. Rollback = move the directory back.
