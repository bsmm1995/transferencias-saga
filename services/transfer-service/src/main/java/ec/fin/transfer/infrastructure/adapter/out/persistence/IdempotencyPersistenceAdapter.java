package ec.fin.transfer.infrastructure.adapter.out.persistence;

import ec.fin.transfer.application.port.out.DuplicateIdempotencyKeyException;
import ec.fin.transfer.application.port.out.IdempotencyStore;
import ec.fin.transfer.infrastructure.adapter.out.persistence.entity.IdempotencyKeyEntity;
import ec.fin.transfer.infrastructure.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;

/**
 * The primary key on the idempotency key is what serializes two concurrent requests carrying the same key.
 * Expiry is checked on read, so an expired key is free again even before the purge job deletes it.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class IdempotencyPersistenceAdapter implements IdempotencyStore {

  private final IdempotencyKeyJpaRepository keys;

  @Override
  public Optional<IdempotencyRecord> findValid(String key, Instant validSince) {
    return keys.findByIdOptional(key)
        .filter(entity -> !entity.getCreatedAt().isBefore(validSince))
        .map(entity -> new IdempotencyRecord(
            entity.getKey(), entity.getRequestFingerprint(), entity.getTransferId(), entity.getCreatedAt()));
  }

  @Override
  public void register(IdempotencyRecord record, Instant validSince) {
    removeIfExpired(record.key(), validSince);
    IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
    entity.setKey(record.key());
    entity.setRequestFingerprint(record.requestFingerprint());
    entity.setTransferId(record.transferId());
    entity.setCreatedAt(record.createdAt());
    keys.persist(entity);
    try {
      keys.flush();
    } catch (PersistenceException e) {
      if (isUniqueViolation(e)) {
        throw new DuplicateIdempotencyKeyException(record.key(), e);
      }
      throw e;
    }
  }

  @Override
  public long purgeExpired(Instant validSince) {
    return keys.delete("createdAt < ?1", validSince);
  }

  // A valid record is left alone: the insert then hits the primary key and the caller replays it.
  private void removeIfExpired(String key, Instant validSince) {
    keys.findByIdOptional(key)
        .filter(existing -> existing.getCreatedAt().isBefore(validSince))
        .ifPresent(expired -> {
          keys.delete(expired);
          keys.flush();
        });
  }

  private static boolean isUniqueViolation(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException) {
        return true;
      }
    }
    return false;
  }
}
