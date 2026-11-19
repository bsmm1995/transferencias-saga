package ec.fin.transfer.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Driven port: remembers which transfer each idempotency key created and with which payload. */
public interface IdempotencyStore {

  /** Returns the record only while it is still valid, i.e. created at or after {@code validSince}. */
  Optional<IdempotencyRecord> findValid(String key, Instant validSince);

  /**
   * Registers the key, replacing an expired record with the same key that the purge job has not removed yet.
   *
   * @throws DuplicateIdempotencyKeyException when a concurrent request registered the same key first
   */
  void register(IdempotencyRecord record, Instant validSince);

  /** Deletes every record created before {@code validSince} and returns how many were removed. */
  long purgeExpired(Instant validSince);

  record IdempotencyRecord(String key, String requestFingerprint, UUID transferId, Instant createdAt) {
  }
}
