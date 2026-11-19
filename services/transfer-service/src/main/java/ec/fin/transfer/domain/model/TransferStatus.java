package ec.fin.transfer.domain.model;

/** Status the customer sees. Derived from the saga state, never stored separately. */
public enum TransferStatus {
  PENDING,
  COMPLETED,
  REJECTED,
  REVERSED,
  FAILED
}
