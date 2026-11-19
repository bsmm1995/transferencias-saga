package ec.fin.commons.error;

/** The request conflicts with the current state of a resource, e.g. an idempotency key reused with another payload. Mapped to HTTP 409. */
public class ConflictException extends DomainException {

  public ConflictException(String code, String message) {
    super(code, message);
  }
}
