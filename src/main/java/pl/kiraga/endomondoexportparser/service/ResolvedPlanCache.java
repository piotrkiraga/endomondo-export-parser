package pl.kiraga.endomondoexportparser.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.model.ResolvedWorkout;

/**
 * In-memory cache of {@link MigrationExecutor#resolveAll} per archive root, so the
 * interactive review page pays for the archive scan, the plan and the place lookups once
 * per application run instead of once per Next/Previous click. Safe to cache because
 * {@code resolveAll} is a function of the archive's files alone — it never reads
 * {@link MigrationLedger}, so a migrate/skip decision has nothing here to invalidate; the
 * review page reads that status fresh from the ledger on every request. Process-local by
 * design: a restart starts cold and resolves from disk again, which is also the recovery
 * path if the archive changes while the application is running.
 */
@Service
public class ResolvedPlanCache {

    private final MigrationExecutor executor;
    private final Map<Path, List<ResolvedWorkout>> plans = new ConcurrentHashMap<>();

    public ResolvedPlanCache(MigrationExecutor executor) {
        this.executor = executor;
    }

    /**
     * Under contention {@code computeIfAbsent} may run the resolve more than once; the
     * extra pass is harmless here (same inputs, same result) and a partially built value
     * can never be published, so no explicit locking is needed.
     */
    public List<ResolvedWorkout> get(Path archiveRoot) {
        return plans.computeIfAbsent(archiveRoot, executor::resolveAll);
    }

}
