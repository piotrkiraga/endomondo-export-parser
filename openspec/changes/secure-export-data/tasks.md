## 1. Relocate personal data

- [x] 1.1 Create `data/` at repo root and move `src/main/resources/endomondo-strava-exports/` into it, dropping the duplicate `_\Nowy folder` and `_\Nowy folder (2)` copies (verify: `src/main/resources/` contains no export files; `data/endomondo-2020-11-01/` intact)
  - Note: `_` actually held 7 `Nowy folder*` subfolders (162 files); all verified byte-identical to `Workouts/` via `cmp` before deletion. Archive also contained loose files (`endomondo-2020-11-01.zip`, Madeira gpx/tcx, Strava export.gpx) — moved along with the rest.
- [x] 1.2 Add `data/` to `.gitignore` (verify: `git check-ignore data/endomondo-2020-11-01/index.html` succeeds and `git status` shows no untracked export files)

## 2. Create anonymized fixtures

- [x] 2.1 Pick one small workout JSON and one small TCX from the archive, copy to `src/test/resources/fixtures/`, and scrub personal data (names, profile IDs, shift GPS coordinates) (verify: manual line-by-line review finds no personal fields)
  - Created three fixtures: `workout-manual.json` (manual entry, location-only points), `workout-tracked.json` (TRACK_MOBILE with altitude/speed/timestamp points, truncated to 8 points), `workout-manual.tcx`. Names replaced, coordinates shifted by a fixed offset; grep for personal markers came back clean.
- [x] 2.2 Confirm fixtures are excluded from the war package since they live on the test classpath (verify: `mvnw package -DskipTests` and inspect `target/*.war` for absence of fixtures)
  - War inspection not possible — build fails on available toolchain (see 3.2). Exclusion holds by construction: Maven never packages `src/test/resources` into the war. Re-verify after the platform-upgrade change restores the build.

## 3. Establish clean baseline

- [x] 3.1 Review and commit the pending `pom.xml` change together with `.gitignore` and fixtures (verify: `git status` clean apart from ignored `data/`)
  - `pom.xml` diff reviewed: fixes `java.version` `1.9`→`9`, adds explicit compiler source/target and UTF-8 encoding. Benign; included in baseline commit.
- [x] 3.2 Run `mvnw clean verify` to record the baseline build result in the change notes, even if it fails on the old toolchain (verify: command output captured; failures documented, not fixed)
  - **Baseline: BUILD FAILS.** JDK 17.0.2 (only 17.x JDKs installed) + Lombok 1.18.12 → `IllegalAccessError: LombokProcessor cannot access JavacProcessingEnvironment` during compile. Lombok gained JDK 16+ support in 1.18.22, so the app cannot compile on this machine until the platform-upgrade change (or an interim Lombok bump). Not fixed here per Non-goals.
