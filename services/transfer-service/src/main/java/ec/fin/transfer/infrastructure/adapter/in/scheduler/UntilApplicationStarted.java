package ec.fin.transfer.infrastructure.adapter.in.scheduler;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled.SkipPredicate;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Skips scheduled jobs while the application is still starting. The scheduler starts before Reactive Messaging
 * wires its channels, so a job firing early fails to obtain the outbox emitter. This observer has the default
 * application priority, which runs after the framework's own startup observers.
 */
@ApplicationScoped
public class UntilApplicationStarted implements SkipPredicate {

  private volatile boolean started;

  void onStart(@Observes StartupEvent event) {
    started = true;
  }

  @Override
  public boolean test(ScheduledExecution execution) {
    return !started;
  }
}
