package ec.fin.corebanking.infrastructure.chaos;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ServiceUnavailableException;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Failure injection for ledger postings, adjustable at runtime through /core/v1/admin/chaos. */
@Slf4j
@ApplicationScoped
public class ChaosMonkey {

  private volatile double failureRate;
  private volatile Mode mode;

  public ChaosMonkey(@ConfigProperty(name = "core.chaos.failure-rate", defaultValue = "0") double failureRate,
      @ConfigProperty(name = "core.chaos.mode", defaultValue = "BEFORE_APPLY") Mode mode) {
    this.failureRate = failureRate;
    this.mode = mode;
  }

  public void configure(double newFailureRate, Mode newMode) {
    failureRate = newFailureRate;
    mode = newMode;
    log.warn("Chaos configured: failure rate {} in mode {}", newFailureRate, newMode);
  }

  public double failureRate() {
    return failureRate;
  }

  public Mode mode() {
    return mode;
  }

  public void maybeFailBeforeApply() {
    maybeFail(Mode.BEFORE_APPLY);
  }

  public void maybeFailAfterApply() {
    maybeFail(Mode.AFTER_APPLY);
  }

  private void maybeFail(Mode phase) {
    if (mode == phase && ThreadLocalRandom.current().nextDouble() < failureRate) {
      log.warn("Chaos: failing posting {}", phase);
      throw new ServiceUnavailableException("Injected failure (" + phase + ")");
    }
  }

  public enum Mode {
    BEFORE_APPLY,
    AFTER_APPLY
  }
}
