## 1. Parser: pictures support

- [x] 1.1 Add `Picture` DTO (created_date, url, point) and `pictures` list to `EndomondoJson`; extend the parser's normalization to `pictures` entries (nested `picture` url and `point`) (verify: `mvnw test` green)
- [x] 1.2 Add a scrubbed picture-bearing fixture (JSON structure only, fake url/coordinates; one entry with a `point` and one without) and regression tests: pictures parsed with coordinates; picture-less fixtures yield empty list (verify: `mvnw test` green)

## 2. Archive survey and sport mapping

- [x] 2.1 Enumerate every distinct `sport` value across the real archive; build the total Endomondo→Strava sport-type mapping as a reviewed table in code, consulting current Strava sport types at developers.strava.com (verify: unit test asserts every archive sport value maps) — five values found: CYCLING_SPORT 107, RUNNING 26, WALKING 24, HIKING 3, CYCLING_TRANSPORTATION 2, mapping to Ride/Run/Walk/Hike — all four confirmed 2026-07-20 as exact members of the Strava API v3 SportType enumeration

## 3. Strava client and OAuth

- [ ] 3.1 Strava API client on `RestClient`: token refresh, TCX upload + status polling, manual activity create, activity update; env-var credentials; tokens persisted to `data/strava-tokens.json` (verify: unit tests against `MockRestServiceServer` for each call incl. 429 backoff and duplicate-rejection handling). Naming falls back to "{time of day} {sport}" when the JSON `name` is blank — decided 2026-07-20, see design.md; 64% of the archive (104/162) needs this.
- [ ] 3.2 OAuth authorization-code flow: connect button → strava.com authorize → `/strava/callback` exchanges the code and stores tokens (verify: mocked-exchange test; manual browser check deferred to smoke)

## 4. Migration engine

- [x] 4.1 Archive scanner pairing JSON+TCX from a configured directory; migration planner producing per-workout actions (upload / manual-create / skip-with-reason) (verify: unit tests over fixture-built archives)
- [ ] 4.2 Ledger (`data/strava-migration-ledger.json`): pending→done/failed lifecycle, resume filtering, duplicate-rejection reconciliation (verify: unit tests incl. crash-between-states case)
- [ ] 4.3 Executor: throttled sequential run over the plan, metadata update after each create/upload, run summary (verify: unit tests with mocked client; re-run test proves no duplicate calls)
- [x] 4.4 Dry-run path shares the planner and performs zero API calls (verify: unit test asserts no interaction with the client) — enforced structurally: `MigrationPlanner` has no Strava collaborator to call, so a plan cannot reach the network

## 5. Photo report

- [x] 5.0 Photo geotagging: resolve each picture's location by precedence (photo EXIF GPS → picture `point` → owning workout's first track point) and write the result into EXIF GPS tags of a **copy** under `data/`, never touching the archive originals; add Apache Commons Imaging for EXIF writing, since the archive's photos carry no EXIF segment at all and one must be created from scratch (verify: unit tests per precedence branch, a round-trip test reading back written coordinates, an idempotency test proving a second pass leaves stamped copies unchanged, and a test asserting archive files are byte-identical afterwards)
- [x] 5.1 Report generator: photos grouped per workout from `pictures`, unmatched-photo section from a `resources/gfx` scan, activity links from the ledger ("pending migration" before), single HTML written to `data/` with relative image paths (verify: unit test on grouping/uniqueness; generated file opened from disk renders images offline) — `PhotoReportGenerator` takes a plain `Map<basename, activityId>` rather than the ledger type directly (4.2 not built yet); the executor will pass the ledger's done-ids once it exists. Reachable from the navbar (`/migration/photo-report`, a "Generate report" button) rather than only from the CLI, since the user wants to browse and refresh it independently of any migration run — verified in a real browser against the real archive: 29 groups, 80 photos, 1 unmatched, images loading through the app's own static resource mapping.

## 6. Web UI

- [ ] 6.1 Migration page: connect status, dry-run preview (plan + sport table + photo counts), execute button, auto-refreshing progress, link to the report file; migration only ever starts from this page (verify: MockMvc tests for page rendering and that boot triggers no Strava interaction)

## 7. End-to-end (user-gated writes)

- [ ] 7.1 Dry-run over the real archive in the running app: 162 workouts planned (136+1 uploads, 25 manual), all sports mapped, 29 photo groups / 81 photos accounted for, report generated (verify: numbers match the archive survey)
- [ ] 7.2 **USER GATE** — smoke migration of 1–2 activities against the real Strava account after the user creates the API app and authorizes; verify in Strava (optionally via the read-only Strava connector) then decide (verify: activity visible with correct name/sport/route)
- [ ] 7.3 **USER GATE** — full migration run on the user's go; regenerate the photo report with live links; reconcile run summary vs. ledger vs. Strava (verify: user confirms activities in Strava apps)
- [ ] 7.4 Reconcile spec deltas against observed behavior; validate; commit (verify: `openspec validate migrate-to-strava` passes; suite green)
