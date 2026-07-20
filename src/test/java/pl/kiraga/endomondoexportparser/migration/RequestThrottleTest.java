package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestThrottleTest {

    @Test
    void secondCallWaitsOutTheInterval() {
        RequestThrottle throttle = new RequestThrottle(100);

        throttle.await();
        long start = System.currentTimeMillis();
        throttle.await();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed >= 90, "expected roughly a 100ms wait, took " + elapsed + "ms");
    }

    @Test
    void noWaitWhenTheIntervalHasAlreadyPassed() {
        RequestThrottle throttle = new RequestThrottle(10);

        throttle.await();
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long start = System.currentTimeMillis();
        throttle.await();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 10, "should not wait when the interval already elapsed, took " + elapsed + "ms");
    }

}
