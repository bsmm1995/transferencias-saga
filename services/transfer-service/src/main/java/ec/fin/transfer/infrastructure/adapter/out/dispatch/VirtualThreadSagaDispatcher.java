package ec.fin.transfer.infrastructure.adapter.out.dispatch;

import ec.fin.commons.web.correlation.CorrelationId;
import ec.fin.transfer.application.port.in.AdvanceSagaUseCase;
import ec.fin.transfer.application.port.out.SagaDispatcher;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** Runs the saga right after the triggering transaction commits, on a virtual thread, so the API answers immediately. */
@Slf4j
@ApplicationScoped
public class VirtualThreadSagaDispatcher implements SagaDispatcher {

  private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(10);

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  // Lazy lookup: the orchestrator depends on this dispatcher, so a direct injection would be circular.
  private final Instance<AdvanceSagaUseCase> sagas;

  public VirtualThreadSagaDispatcher(Instance<AdvanceSagaUseCase> sagas) {
    this.sagas = sagas;
  }

  @Override
  public void dispatch(UUID transferId) {
    String correlationId = CorrelationId.current().orElse(null);
    executor.execute(() -> {
      CorrelationId.bind(correlationId);
      try {
        sagas.get().advance(transferId);
      } catch (RuntimeException e) {
        log.warn("Saga of transfer {} stopped; the scheduler will resume it", transferId, e);
      } finally {
        CorrelationId.clear();
      }
    });
  }

  /** Lets in-flight steps record their outcome; anything still running afterwards is resumed by the scheduler. */
  @PreDestroy
  void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
        log.warn("Saga dispatcher did not drain within {}; pending sagas will be resumed by the scheduler", SHUTDOWN_GRACE);
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
