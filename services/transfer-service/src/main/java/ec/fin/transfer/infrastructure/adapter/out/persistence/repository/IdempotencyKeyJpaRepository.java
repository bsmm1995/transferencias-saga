package ec.fin.transfer.infrastructure.adapter.out.persistence.repository;

import ec.fin.transfer.infrastructure.adapter.out.persistence.entity.IdempotencyKeyEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/** Panache repository of idempotency keys. */
@ApplicationScoped
public class IdempotencyKeyJpaRepository implements PanacheRepositoryBase<IdempotencyKeyEntity, String> {
}
