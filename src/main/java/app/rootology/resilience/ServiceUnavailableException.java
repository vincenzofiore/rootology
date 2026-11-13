package app.rootology.resilience;

/** An external service is down or its circuit is open; the message is fit to show to a person. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
