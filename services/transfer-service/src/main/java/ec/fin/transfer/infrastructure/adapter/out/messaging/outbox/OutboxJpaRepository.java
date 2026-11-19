package ec.fin.transfer.infrastructure.adapter.out.messaging.outbox;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

/** Panache repository of the outbox table. */
@ApplicationScoped
public class OutboxJpaRepository implements PanacheRepository<OutboxMessageEntity> {

  // Arbitrary but fixed: every replica must compete for the same advisory lock key.
  private static final long RELAY_LOCK_KEY = 0x7472_616e_7366_6572L;

  /**
   * Transaction-scoped PostgreSQL advisory lock. Only one replica relays at a time; without it, two replicas
   * using SKIP LOCKED would split consecutive rows of the same transfer and could publish them out of order.
   * Released automatically on commit or rollback.
   */
  public boolean tryAcquireRelayLock() {
    return (Boolean) getEntityManager()
        .createNativeQuery("select pg_try_advisory_xact_lock(:key)", Boolean.class)
        .setParameter("key", RELAY_LOCK_KEY)
        .getSingleResult();
  }

  /** SKIP LOCKED keeps the purge job and a relay that lost the advisory race from waiting on these rows. */
  @SuppressWarnings("unchecked")
  public List<OutboxMessageEntity> lockPendingBatch(int limit) {
    return getEntityManager()
        .createNativeQuery("""
            select * from outbox_message
            where status = 'PENDING'
            order by id
            limit :limit
            for update skip locked""", OutboxMessageEntity.class)
        .setParameter("limit", limit)
        .getResultList();
  }

  public long deletePublishedBefore(Instant threshold) {
    return delete("status = ?1 and publishedAt < ?2", OutboxMessageEntity.Status.PUBLISHED, threshold);
  }
}
