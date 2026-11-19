package ec.fin.transfer.application.port.in;

import ec.fin.transfer.domain.model.Transfer;
import ec.fin.transfer.domain.saga.SagaStepLog;
import java.util.List;
import java.util.UUID;

/** Driving port: reads a transfer together with its saga journal. */
public interface GetTransferUseCase {

  TransferView get(UUID transferId);

  record TransferView(Transfer transfer, List<SagaStepLog> journal) {
  }
}
