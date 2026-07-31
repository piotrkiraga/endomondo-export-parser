package pl.kiraga.endomondoexportparser.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.model.AppStatus;
import pl.kiraga.endomondoexportparser.model.LedgerEntry;
import pl.kiraga.endomondoexportparser.model.LedgerStatus;

/**
 * Assembles the home page's status snapshot from the three local stores that already
 * hold it. Every read is local: nothing here reaches Strava or any other network.
 */
@Service
public class AppStatusResolver {

    private static final String WORKOUTS_DIR = "Workouts";

    private final ArchiveScanner scanner;
    private final MigrationLedger ledger;
    private final LocationCache locationCache;

    public AppStatusResolver(ArchiveScanner scanner, MigrationLedger ledger, LocationCache locationCache) {
        this.scanner = scanner;
        this.ledger = ledger;
        this.locationCache = locationCache;
    }

    /** {@code archiveRoot} is the archive root (containing "Workouts"), matching {@link MigrationExecutor#run}. */
    public AppStatus resolve(Path archiveRoot) {

        List<LedgerEntry> entries = ledger.entries();

        return new AppStatus(
                archiveWorkoutCount(archiveRoot),
                count(entries, LedgerStatus.DONE),
                count(entries, LedgerStatus.FAILED),
                count(entries, LedgerStatus.SKIPPED),
                count(entries, LedgerStatus.PENDING),
                locationCache.size());

    }

    private Integer archiveWorkoutCount(Path archiveRoot) {
        Path workoutsDirectory = archiveRoot.resolve(WORKOUTS_DIR);
        if (!Files.isDirectory(workoutsDirectory)) {
            return null;
        }
        return scanner.scan(workoutsDirectory).workouts().size();
    }

    private int count(List<LedgerEntry> entries, LedgerStatus status) {
        return (int) entries.stream().filter(entry -> entry.status() == status).count();
    }

}
