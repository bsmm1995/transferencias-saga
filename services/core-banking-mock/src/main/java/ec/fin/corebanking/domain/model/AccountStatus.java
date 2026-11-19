package ec.fin.corebanking.domain.model;

/** Lifecycle of an account. Only ACTIVE accounts can be debited; CLOSED accounts cannot receive credits. */
public enum AccountStatus {
  ACTIVE,
  BLOCKED,
  CLOSED
}
