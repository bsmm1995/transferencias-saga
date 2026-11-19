package ec.fin.commons.error;

/** A valid request that breaks a business rule (insufficient funds, unsupported currency...). Mapped to HTTP 422. */
public class BusinessRuleViolationException extends DomainException {

  public BusinessRuleViolationException(String code, String message) {
    super(code, message);
  }
}
