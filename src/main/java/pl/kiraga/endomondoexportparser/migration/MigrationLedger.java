package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/**
 * Tracks each workout's migration outcome across runs, persisted to
 * {@code data/strava-migration-ledger.json} (git-ignored with the rest of personal
 * data). An entry is written {@link LedgerStatus#PENDING} before the Strava call that
 * would create/upload it, then finalized {@link LedgerStatus#DONE} or
 * {@link LedgerStatus#FAILED} after — so an interrupted run leaves a PENDING row as a
 * visible reconciliation point instead of a silent duplicate (design.md).
 *
 * <p>Only DONE counts as already handled ({@link #isDone}): PENDING and FAILED are both
 * retried on the next run. A retried UPLOAD_TCX is backstopped by Strava's own
 * external_id duplicate detection — whatever activity id that produces is simply
 * recorded via {@link #markDone}. CREATE_MANUAL has no such backstop; per design.md that
 * risk is accepted and left to the user to check, since manual workouts are few.
 */
@Service
public class MigrationLedger {

    private final Path file;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    public MigrationLedger() {
        this(Path.of("data", "strava-migration-ledger.json"), Clock.systemUTC());
    }

    MigrationLedger(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    public void markPending(String basename, PlannedAction action) {
        lock.lock();
        try {
            Map<String, LedgerEntry> current = load();
            current.put(basename, LedgerEntry.pending(basename, action, now()));
            save(current);
        } finally {
            lock.unlock();
        }
    }

    public void markDone(String basename, long activityId) {
        update(basename, entry -> entry.asDone(activityId, now()));
    }

    public void markFailed(String basename, String reason) {
        update(basename, entry -> entry.asFailed(reason, now()));
    }

    public Optional<LedgerEntry> find(String basename) {
        lock.lock();
        try {
            return Optional.ofNullable(load().get(basename));
        } finally {
            lock.unlock();
        }
    }

    /** Only a DONE entry counts as already handled; PENDING/FAILED are retried on resume. */
    public boolean isDone(String basename) {
        return find(basename).map(entry -> entry.status() == LedgerStatus.DONE).orElse(false);
    }

    /** basename to activity id for every DONE entry, e.g. to wire into {@link PhotoReportGenerator}. */
    public Map<String, Long> activityIdsByBasename() {
        lock.lock();
        try {
            Map<String, Long> ids = new LinkedHashMap<>();
            load().forEach((basename, entry) -> {
                if (entry.status() == LedgerStatus.DONE) {
                    ids.put(basename, entry.activityId());
                }
            });
            return ids;
        } finally {
            lock.unlock();
        }
    }

    public List<LedgerEntry> entries() {
        lock.lock();
        try {
            return load().values().stream().sorted(Comparator.comparing(LedgerEntry::basename)).toList();
        } finally {
            lock.unlock();
        }
    }

    private void update(String basename, UnaryOperator<LedgerEntry> updater) {
        lock.lock();
        try {
            Map<String, LedgerEntry> current = load();
            LedgerEntry existing = current.get(basename);
            if (existing == null) {
                throw new IllegalStateException(
                        "No pending ledger entry for " + basename + "; markPending must run first");
            }
            current.put(basename, updater.apply(existing));
            save(current);
        } finally {
            lock.unlock();
        }
    }

    private Map<String, LedgerEntry> load() {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            LedgerEntry[] stored = mapper.readValue(Files.readAllBytes(file), LedgerEntry[].class);
            Map<String, LedgerEntry> map = new LinkedHashMap<>();
            for (LedgerEntry entry : stored) {
                map.put(entry.basename(), entry);
            }
            return map;
        } catch (IOException | RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    private void save(Map<String, LedgerEntry> current) {
        try {
            Files.createDirectories(file.toAbsolutePath().normalize().getParent());
            List<LedgerEntry> sorted = new ArrayList<>(current.values());
            sorted.sort(Comparator.comparing(LedgerEntry::basename));
            Files.writeString(file, mapper.writeValueAsString(sorted), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write migration ledger to " + file, e);
        }
    }

    private String now() {
        return clock.instant().toString();
    }

}
