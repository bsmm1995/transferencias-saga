package ec.fin.transfer.application.service;

import ec.fin.transfer.application.idempotency.IdempotencyPolicy;
import ec.fin.transfer.application.port.in.PurgeExpiredIdempotencyKeysUseCase;
import ec.fin.transfer.application.port.out.IdempotencyStore;
import ec.fin.transfer.application.port.out.UnitOfWork;
import java.time.Clock;
import lombok.RequiredArgsConstructor;

/** Use case: deletes idempotency keys older than the retention of the {@link IdempotencyPolicy}. */
@RequiredArgsConstructor
public class IdempotencyKeyPurgeService implements PurgeExpiredIdempotencyKeysUseCase {

  private final IdempotencyStore idempotency;
  private final UnitOfWork unitOfWork;
  private final IdempotencyPolicy policy;
  private final Clock clock;

  @Override
  public long purgeExpired() {
    return unitOfWork.inTransaction(() -> idempotency.purgeExpired(policy.validSince(clock.instant())));
  }
}
