package ec.fin.transfer.application.port.out;

import java.util.UUID;

/** Optimistic locking lost: another worker already moved this saga forward. */
public class ConcurrentUpdateException extends RuntimeException {

  public ConcurrentUpdateException(UUID transferId, Throwable cause) {
    super("Transfer " + transferId + " was modified concurrently", cause);
  }
}
