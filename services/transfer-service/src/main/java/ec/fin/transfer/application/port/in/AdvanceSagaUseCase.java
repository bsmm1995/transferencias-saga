package ec.fin.transfer.application.port.in;

import java.util.UUID;

/** Driving port: moves sagas forward. Used by the post-commit dispatcher and by the recovery scheduler. */
public interface AdvanceSagaUseCase {

  /** Runs every step of the saga that is due now, stopping when it has to wait or reaches a final state. */
  void advance(UUID transferId);

  /** Safety net: advances the sagas whose next attempt is overdue (retries, timeouts, lost dispatches). */
  int advanceDue();
}
