package ec.fin.commons.error;

import lombok.Getter;

/** Base type for errors that carry a stable, machine-readable code the API exposes to clients. */
@Getter
public abstract class DomainException extends RuntimeException {

  private final String code;

  protected DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  protected DomainException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }
}
