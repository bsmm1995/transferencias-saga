package ec.fin.transfer.infrastructure.adapter.out.persistence.repository;

import ec.fin.transfer.infrastructure.adapter.out.persistence.entity.SagaStepEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

/** Panache repository of the saga journal. */
@ApplicationScoped
public class SagaStepJpaRepository implements PanacheRepository<SagaStepEntity> {

  public List<SagaStepEntity> findJournal(UUID transferId) {
    return list("transferId = ?1 order by id", transferId);
  }
}
