package ec.fin.transfer.application.port.out;

import ec.fin.transfer.domain.event.TransferEvent;
import java.util.List;

/** Transactional outbox: must be called inside the transaction that changes the aggregate. */
public interface EventOutbox {

  void append(String correlationId, List<TransferEvent> events);
}
