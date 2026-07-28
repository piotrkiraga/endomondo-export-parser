package pl.kiraga.endomondoexportparser.model;

import java.util.List;
import pl.kiraga.endomondoexportparser.service.MigrationExecutor;

/**
 * What one {@link MigrationExecutor#run} pass actually did. {@code alreadyDone} counts
 * workouts the ledger already had marked {@link LedgerStatus#DONE} from an earlier run;
 * {@code skippedByUser} counts ones durably {@link LedgerStatus#SKIPPED} from the
 * interactive review page (task 6.1) — both left alone without any Strava call, so a
 * resumed run's numbers still add up to the whole plan. {@code failures} are workouts
 * nothing was created for; the distinct {@code metadataWarnings} are workouts that WERE
 * created/uploaded (and are safely {@code DONE} in the ledger either way) but whose
 * trailing name/sport-type/description correction call failed — worth a human glance,
 * not a migration failure.
 */
public record MigrationRunSummary(
        int uploaded,
        int manualCreated,
        int alreadyDone,
        int skippedByPlanner,
        int skippedByUser,
        int failed,
        List<String> failures,
        List<String> metadataWarnings) {

    public int totalHandled() {
        return uploaded + manualCreated + alreadyDone;
    }

}
