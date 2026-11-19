package ec.fin.transfer.application.port.in;

/** Driving port: housekeeping that removes idempotency keys past their retention. */
public interface PurgeExpiredIdempotencyKeysUseCase {

  /** Returns how many keys were removed. */
  long purgeExpired();
}
