package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.kiraga.endomondoexportparser.model.PlannedAction;
import pl.kiraga.endomondoexportparser.model.ResolvedWorkout;

public class ResolvedPlanCacheTest {

    private static final Path FIRST_ARCHIVE = Path.of("archives", "endomondo-2020");
    private static final Path SECOND_ARCHIVE = Path.of("archives", "endomondo-2019");

    /** Counts resolves without touching a real archive; {@link MigrationExecutor}'s collaborators are never used. */
    private static final class RecordingExecutor extends MigrationExecutor {

        private final List<Path> resolvedRoots = new ArrayList<>();

        private RecordingExecutor() {
            super(null, null, null, null, null);
        }

        @Override
        public List<ResolvedWorkout> resolveAll(Path archiveRoot) {
            resolvedRoots.add(archiveRoot);
            return List.of(workout(archiveRoot.getFileName().toString()));
        }

    }

    private static ResolvedWorkout workout(String basename) {
        return new ResolvedWorkout(basename, PlannedAction.UPLOAD_TCX, null, "Morning Ride", "Ride", null,
                null, null, null, null, null, 0);
    }

    @Test
    void resolvesOnceAndReusesTheSameResultForTheSameArchiveRoot() {
        RecordingExecutor executor = new RecordingExecutor();
        ResolvedPlanCache cache = new ResolvedPlanCache(executor);

        List<ResolvedWorkout> first = cache.get(FIRST_ARCHIVE);
        List<ResolvedWorkout> second = cache.get(FIRST_ARCHIVE);

        assertEquals(List.of(FIRST_ARCHIVE), executor.resolvedRoots);
        assertSame(first, second);
    }

    @Test
    void cachesEachArchiveRootIndependently() {
        RecordingExecutor executor = new RecordingExecutor();
        ResolvedPlanCache cache = new ResolvedPlanCache(executor);

        List<ResolvedWorkout> first = cache.get(FIRST_ARCHIVE);
        List<ResolvedWorkout> second = cache.get(SECOND_ARCHIVE);
        cache.get(FIRST_ARCHIVE);
        cache.get(SECOND_ARCHIVE);

        assertEquals(List.of(FIRST_ARCHIVE, SECOND_ARCHIVE), executor.resolvedRoots);
        assertEquals("endomondo-2020", first.get(0).basename());
        assertEquals("endomondo-2019", second.get(0).basename());
    }

}
