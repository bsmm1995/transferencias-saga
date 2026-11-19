package ec.fin.corebanking.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/** Aggregate: one posting on the ledger, identified by the operation id the caller chose, which makes postings idempotent. */
@Getter
public final class LedgerOperation {

  private final UUID id;
  private final OperationType type;
  private final String accountNumber;
  private final BigDecimal amount;
  private final String currency;
  private final String reference;
  private final Instant appliedAt;
  private Status status;
  private Instant reversedAt;

  private LedgerOperation(UUID id, OperationType type, String accountNumber, BigDecimal amount, String currency,
      String reference, Instant appliedAt, Status status) {
    this.id = Objects.requireNonNull(id, "id");
    this.type = type;
    this.accountNumber = accountNumber;
    this.amount = amount;
    this.currency = currency;
    this.reference = reference;
    this.appliedAt = appliedAt;
    this.status = status;
  }

  public static LedgerOperation applied(UUID id, OperationType type, String accountNumber, BigDecimal amount,
      String currency, String reference, Instant now) {
    return new LedgerOperation(id, type, accountNumber, amount.setScale(2), currency, reference, now, Status.APPLIED);
  }

  /**
   * Tombstone left when a reversal arrives before the operation itself (e.g. a delayed request). It makes the
   * late operation fail instead of moving money that was already compensated.
   */
  public static LedgerOperation voided(UUID id, Instant now) {
    LedgerOperation tombstone = new LedgerOperation(id, null, null, null, null, null, now, Status.VOIDED);
    tombstone.reversedAt = now;
    return tombstone;
  }

  public boolean samePayload(OperationType otherType, String otherAccount, BigDecimal otherAmount, String otherCurrency) {
    return type == otherType
        && Objects.equals(accountNumber, otherAccount)
        && amount.compareTo(otherAmount) == 0
        && Objects.equals(currency, otherCurrency);
  }

  /** Signed effect a reversal must apply to the account. */
  public BigDecimal reversalDelta() {
    return type == OperationType.DEBIT ? amount : amount.negate();
  }

  public void markReversed(Instant now) {
    status = Status.REVERSED;
    reversedAt = now;
  }

  public enum Status {
    APPLIED,
    REVERSED,
    VOIDED
  }
}
