## ADDED Requirements

### Requirement: The review page's resolved plan is cached across requests, not recomputed per request
The interactive migration review page SHALL reuse a previously resolved plan for the current archive across requests within the same running application instance, rather than re-scanning the archive and re-resolving every workout on each request. The cached plan SHALL be invalidated for a workout the moment a migrate or skip decision is recorded for it through the review page itself, so the page always reflects that decision without requiring an application restart.

#### Scenario: Revisiting the review page reuses the prior resolution
- **WHEN** the review page is requested again for the same archive within the same running application instance
- **THEN** the response reflects the previously resolved plan without re-scanning the archive from disk

#### Scenario: A decision made through the review page is reflected immediately
- **WHEN** the user migrates or skips a workout from the review page
- **THEN** the page's next view of that workout, and its own redirect logic, show the updated decision without needing an application restart

#### Scenario: A fresh application start resolves the plan again
- **WHEN** the application restarts
- **THEN** the next review-page request resolves the plan from the archive and ledger from scratch, exactly as it did before this change
