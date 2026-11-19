package ec.fin.transfer.application.port.out;

import java.util.function.Supplier;

/**
 * Explicit transaction boundary. Each saga step is a short local transaction and remote calls always run
 * outside of it, so no database connection or lock is held while waiting on the network.
 */
public interface UnitOfWork {

  <T> T inTransaction(Supplier<T> work);

  default void inTransaction(Runnable work) {
    inTransaction(() -> {
      work.run();
      return null;
    });
  }
}
