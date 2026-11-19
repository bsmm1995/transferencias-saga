package ec.fin.corebanking.domain.model;

/** Result of a reversal request. Every value is a success for the caller: reversals are idempotent and tolerant. */
public enum ReversalOutcome {
  REVERSED,
  ALREADY_REVERSED,
  NOT_FOUND
}
