package ec.fin.transfer.infrastructure.adapter.out.persistence;

import ec.fin.transfer.application.port.in.GetTransferUseCase.TransferView;
import ec.fin.transfer.application.port.out.ConcurrentUpdateException;
import ec.fin.transfer.application.port.out.TransferReadModel;
import ec.fin.transfer.application.port.out.TransferRepository;
import ec.fin.transfer.domain.model.Transfer;
import ec.fin.transfer.infrastructure.adapter.out.persistence.entity.TransferEntity;
import ec.fin.transfer.infrastructure.adapter.out.persistence.mapper.TransferEntityMapper;
import ec.fin.transfer.infrastructure.adapter.out.persistence.repository.SagaStepJpaRepository;
import ec.fin.transfer.infrastructure.adapter.out.persistence.repository.TransferJpaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.StaleStateException;

/** Driven adapter: persists the transfer aggregate, its journal and its version with Hibernate ORM. */
@ApplicationScoped
@RequiredArgsConstructor
public class TransferPersistenceAdapter implements TransferRepository, TransferReadModel {

  private final TransferJpaRepository transfers;
  private final SagaStepJpaRepository steps;

  @Override
  public Optional<Transfer> findById(UUID id) {
    return transfers.findByIdOptional(id).map(TransferEntityMapper::toDomain);
  }

  @Override
  public void save(Transfer transfer) {
    TransferEntity entity = transfers.findById(transfer.getId());
    if (entity == null) {
      transfers.persist(TransferEntityMapper.newEntity(transfer));
    } else {
      if (entity.getVersion() != transfer.getVersion()) {
        throw new ConcurrentUpdateException(transfer.getId(), null);
      }
      TransferEntityMapper.copySagaState(transfer, entity);
    }
    transfer.pullStepLogs().forEach(log -> steps.persist(TransferEntityMapper.toEntity(transfer.getId(), log)));
    try {
      // Flushing here turns a lost optimistic lock into a typed error inside the unit of work.
      transfers.flush();
    } catch (OptimisticLockException | StaleStateException e) {
      throw new ConcurrentUpdateException(transfer.getId(), e);
    }
  }

  @Override
  public List<UUID> findDueIds(Instant now, int limit) {
    return transfers.findDueIds(now, limit);
  }

  @Override
  public Optional<TransferView> findView(UUID id) {
    return findById(id).map(transfer -> new TransferView(
        transfer,
        steps.findJournal(id).stream().map(TransferEntityMapper::toDomain).toList()));
  }
}
