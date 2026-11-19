package ec.fin.contracts.messaging;

/** Topic names shared by producers and consumers. Kafka plays the role EventBridge + SQS play in the target AWS design. */
public final class Topics {

  public static final String PAYMENT_ORDERS = "payment-orders";
  public static final String PAYMENT_RESULTS = "payment-results";
  public static final String TRANSFER_EVENTS = "transfer-events";

  private Topics() {
  }
}
