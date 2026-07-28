package pl.kiraga.endomondoexportparser.util;

/**
 * Enforces a minimum interval between calls to a shared external service, so a batch of
 * lookups across up to 162 workouts can't burst past a provider's rate limit.
 */
public final class RequestThrottleUtil {

    private final long minIntervalMillis;
    private long lastCallAt = 0;

    public RequestThrottleUtil(long minIntervalMillis) {
        this.minIntervalMillis = minIntervalMillis;
    }

    public synchronized void await() {
        long wait = lastCallAt + minIntervalMillis - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallAt = System.currentTimeMillis();
    }

}
