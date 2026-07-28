package pl.kiraga.endomondoexportparser.exception;

/** Wraps any Strava API failure (HTTP error, missing credentials, missing tokens) uniformly. */
public class StravaApiException extends RuntimeException {

    public StravaApiException(String message) {
        super(message);
    }

    public StravaApiException(String message, Throwable cause) {
        super(message, cause);
    }

}
