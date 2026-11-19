package ec.fin.transfer.application.port.out;

/** Raised when a concurrent request registered the same idempotency key first. */
public class DuplicateIdempotencyKeyException extends RuntimeException {

  public DuplicateIdempotencyKeyException(String key, Throwable cause) {
    super("Idempotency key already registered: " + key, cause);
  }
}
