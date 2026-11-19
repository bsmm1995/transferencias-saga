package ec.fin.paymentconnector.infrastructure.adapter.out.clearing;

import ec.fin.paymentconnector.application.port.out.ClearingNetworkPort;
import ec.fin.paymentconnector.domain.model.ClearingResult;
import ec.fin.paymentconnector.domain.model.PaymentOrder;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Fake central bank. The creditor account suffix picks the scenario so every saga branch can be triggered:
 * 0000 rejects with AC01 (incorrect account), 4040 rejects with AC04 (closed account), 7777 never answers.
 */
@ApplicationScoped
public class SimulatedBceNetwork implements ClearingNetworkPort {

  private final Duration rpiLatency;
  private final Duration spiLatency;
  private final Clock clock = Clock.systemUTC();

  public SimulatedBceNetwork(
      @ConfigProperty(name = "connector.rpi-latency") Duration rpiLatency,
      @ConfigProperty(name = "connector.spi-latency") Duration spiLatency) {
    this.rpiLatency = rpiLatency;
    this.spiLatency = spiLatency;
  }

  @Override
  public Optional<ClearingResult> submit(PaymentOrder order) {
    sleep(order.network() == PaymentOrder.ClearingNetwork.RPI ? rpiLatency : spiLatency);
    String account = order.creditorAccount();
    if (account.endsWith("7777")) {
      return Optional.empty();
    }
    if (account.endsWith("0000")) {
      return Optional.of(ClearingResult.rejected(order, "AC01", "Incorrect account number at the creditor bank", clock.instant()));
    }
    if (account.endsWith("4040")) {
      return Optional.of(ClearingResult.rejected(order, "AC04", "Closed account at the creditor bank", clock.instant()));
    }
    return Optional.of(ClearingResult.settled(order, clock.instant()));
  }

  private static void sleep(Duration latency) {
    try {
      Thread.sleep(latency);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
