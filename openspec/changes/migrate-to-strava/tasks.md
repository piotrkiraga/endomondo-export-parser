## 1. Parser: pictures support

- [ ] 1.1 Add `Picture` DTO (created_date, url, point) and `pictures` list to `EndomondoJson`; extend the parser's normalization to `pictures` entries (nested `picture` url and `point`) (verify: `mvnw test` green)
- [ ] 1.2 Add a scrubbed picture-bearing fixture (JSON structure only, fake url/coordinates) and regression tests: pictures parsed; picture-less fixtures yield empty list (verify: `mvnw test` green)

## 2. Archive survey and sport mapping

- [ ] 2.1 Enumerate every distinct `sport` value across the real archive; build the total Endomondo→Strava sport-type mapping as a reviewed table in code, consulting current Strava sport types at developers.strava.com (verify: unit test asserts every archive sport value maps)

## 3. Strava client and OAuth

- [ ] 3.1 Strava API client on `RestClient`: token refresh, TCX upload + status polling, manual activity create, activity update; env-var credentials; tokens persisted to `data/strava-tokens.json` (verify: unit tests against `MockRestServiceServer` for each call incl. 429 backoff and duplicate-rejection handling)
- [ ] 3.2 OAuth authorization-code flow: connect button → strava.com authorize → `/strava/callback` exchanges the code and stores tokens (verify: mocked-exchange test; manual browser check deferred to smoke)

## 4. Migration engine

- [ ] 4.1 Archive scanner pairing JSON+TCX from a configured directory; migration planner producing per-workout actions (upload / manual-create / skip-with-reason) (verify: unit tests over fixture-built archives)
- [ ] 4.2 Ledger (`data/strava-migration-ledger.json`): pending→done/failed lifecycle, resume filtering, duplicate-rejection reconciliation (verify: unit tests incl. crash-between-states case)
- [ ] 4.3 Executor: throttled sequential run over the plan, metadata update after each create/upload, run summary (verify: unit tests with mocked client; re-run test proves no duplicate calls)
- [ ] 4.4 Dry-run path shares the planner and performs zero API calls (verify: unit test asserts no interaction with the client)

## 5. Photo report

- [ ] 5.1 Report generator: photos grouped per workout from `pictures`, unmatched-photo section from a `resources/gfx` scan, activity links from the ledger ("pending migration" before), single HTML written to `data/` with relative image paths (verify: unit test on grouping/uniqueness; generated file opened from disk renders images offline)

## 6. Web UI

- [ ] 6.1 Migration page: connect status, dry-run preview (plan + sport table + photo counts), execute button, auto-refreshing progress, link to the report file; migration only ever starts from this page (verify: MockMvc tests for page rendering and that boot triggers no Strava interaction)

## 7. End-to-end (user-gated writes)

- [ ] 7.1 Dry-run over the real archive in the running app: 162 workouts planned (136+1 uploads, 25 manual), all sports mapped, 29 photo groups / 81 photos accounted for, report generated (verify: numbers match the archive survey)
- [ ] 7.2 **USER GATE** — smoke migration of 1–2 activities against the real Strava account after the user creates the API app and authorizes; verify in Strava (optionally via the read-only Strava connector) then decide (verify: activity visible with correct name/sport/route)
- [ ] 7.3 **USER GATE** — full migration run on the user's go; regenerate the photo report with live links; reconcile run summary vs. ledger vs. Strava (verify: user confirms activities in Strava apps)
- [ ] 7.4 Reconcile spec deltas against observed behavior; validate; commit (verify: `openspec validate migrate-to-strava` passes; suite green)
