package ec.fin.transfer.application.port.out;

import ec.fin.transfer.domain.model.Transfer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driven port: persistence of the transfer aggregate and its saga state. */
public interface TransferRepository {

  Optional<Transfer> findById(UUID id);

  /**
   * Inserts or updates the aggregate and appends its new journal lines.
   *
   * @throws ConcurrentUpdateException when another worker changed the transfer since it was loaded
   */
  void save(Transfer transfer);

  List<UUID> findDueIds(Instant now, int limit);
}
