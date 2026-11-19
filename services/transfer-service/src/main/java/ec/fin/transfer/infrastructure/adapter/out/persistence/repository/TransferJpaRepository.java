package ec.fin.transfer.infrastructure.adapter.out.persistence.repository;

import ec.fin.transfer.domain.saga.SagaState;
import ec.fin.transfer.infrastructure.adapter.out.persistence.entity.TransferEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Panache repository of transfers, including the due-saga query used by the recovery job. */
@ApplicationScoped
public class TransferJpaRepository implements PanacheRepositoryBase<TransferEntity, UUID> {

  public List<UUID> findDueIds(Instant now, int limit) {
    return getEntityManager()
        .createQuery("""
            select t.id from TransferEntity t
            where t.sagaState in :active and t.nextAttemptAt <= :now
            order by t.nextAttemptAt""", UUID.class)
        .setParameter("active", SagaState.active())
        .setParameter("now", now)
        .setMaxResults(limit)
        .getResultList();
  }
}
