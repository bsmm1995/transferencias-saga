package ec.fin.contracts.messaging;

/** Kafka header names shared by every producer and consumer. */
public final class MessageHeaders {

  public static final String EVENT_ID = "event-id";
  public static final String EVENT_TYPE = "event-type";
  public static final String CORRELATION_ID = "correlation-id";

  private MessageHeaders() {
  }
}
