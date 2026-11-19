package ec.fin.transfer;

import ec.fin.transfer.infrastructure.adapter.in.messaging.PaymentResultConsumer;
import ec.fin.transfer.infrastructure.adapter.out.messaging.outbox.OutboxRelay;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import java.util.HashMap;
import java.util.Map;

/** Swaps Kafka for the in-memory connector so the saga can be driven from the test without a broker. */
public class InMemoryMessagingResource implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {
    Map<String, String> config = new HashMap<>();
    config.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(OutboxRelay.CHANNEL));
    config.putAll(InMemoryConnector.switchIncomingChannelsToInMemory(PaymentResultConsumer.CHANNEL));
    return config;
  }

  @Override
  public void stop() {
    InMemoryConnector.clear();
  }
}
