package ec.fin.transfer.application.port.out;

import ec.fin.transfer.application.port.in.GetTransferUseCase.TransferView;
import java.util.Optional;
import java.util.UUID;

/** Driven port: query side, returns a transfer with its saga journal. */
public interface TransferReadModel {

  Optional<TransferView> findView(UUID id);
}
