package ec.fin.transfer.application.service;

import ec.fin.commons.error.ResourceNotFoundException;
import ec.fin.transfer.application.port.in.GetTransferUseCase;
import ec.fin.transfer.application.port.out.TransferReadModel;
import ec.fin.transfer.application.port.out.UnitOfWork;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Use case: reads a transfer and its saga journal. */
@RequiredArgsConstructor
public class TransferQueryService implements GetTransferUseCase {

  private final TransferReadModel readModel;
  private final UnitOfWork unitOfWork;

  @Override
  public TransferView get(UUID transferId) {
    return unitOfWork.inTransaction(() -> readModel.findView(transferId))
        .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));
  }
}
