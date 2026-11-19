package ec.fin.transfer.infrastructure.adapter.out.ledger;

import lombok.Getter;

/** Definitive refusal from the core (any 4xx except 408/429). Excluded from the circuit breaker failure count. */
@Getter
public class LedgerRejectedException extends RuntimeException {

  private final String code;
  private final String detail;

  public LedgerRejectedException(String code, String detail) {
    super(code + (detail == null ? "" : ": " + detail));
    this.code = code;
    this.detail = detail;
  }
}
