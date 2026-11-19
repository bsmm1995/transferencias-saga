package ec.fin.transfer.application.port.out;

import java.util.UUID;

/** Asks for a saga to be advanced soon, off the caller's thread. Best effort: the scheduler recovers lost dispatches. */
public interface SagaDispatcher {

  void dispatch(UUID transferId);
}
