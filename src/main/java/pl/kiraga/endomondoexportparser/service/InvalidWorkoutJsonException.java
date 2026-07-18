package pl.kiraga.endomondoexportparser.service;

/**
 * Raised for any upload that cannot be understood as an Endomondo workout export:
 * syntactically invalid JSON as well as valid JSON of the wrong shape. The upload
 * flow catches this single type to render the user-facing invalid-format message,
 * keeping callers decoupled from the underlying JSON library's exception hierarchy.
 */
public class InvalidWorkoutJsonException extends RuntimeException {

    public InvalidWorkoutJsonException(String message) {
        super(message);
    }

    public InvalidWorkoutJsonException(String message, Throwable cause) {
        super(message, cause);
    }

}
