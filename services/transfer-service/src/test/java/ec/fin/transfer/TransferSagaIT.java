package ec.fin.transfer;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.fin.commons.error.DependencyUnavailableException;
import ec.fin.commons.web.correlation.CorrelationId;
import ec.fin.contracts.messaging.PaymentOrderMessage;
import ec.fin.contracts.messaging.PaymentResultMessage;
import ec.fin.contracts.messaging.Topics;
import ec.fin.transfer.application.port.in.PurgeExpiredIdempotencyKeysUseCase;
import ec.fin.transfer.application.port.out.IdempotencyStore;
import ec.fin.transfer.application.port.out.IdempotencyStore.IdempotencyRecord;
import ec.fin.transfer.application.port.out.LedgerPort;
import ec.fin.transfer.application.port.out.LedgerPort.LedgerResult;
import ec.fin.transfer.application.port.out.RiskAssessmentPort;
import ec.fin.transfer.application.port.out.RiskAssessmentPort.RiskAssessment;
import ec.fin.transfer.application.port.out.UnitOfWork;
import ec.fin.transfer.infrastructure.adapter.in.messaging.PaymentResultConsumer;
import ec.fin.transfer.infrastructure.adapter.out.messaging.outbox.OutboxRelay;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end saga inside the service: real PostgreSQL (Dev Services), real outbox and scheduler, participants
 * replaced by mocks at their ports and the broker by the in-memory connector.
 */
@QuarkusTest
@QuarkusTestResource(InMemoryMessagingResource.class)
class TransferSagaIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  @InjectMock
  LedgerPort ledger;

  @InjectMock
  RiskAssessmentPort risk;

  @Inject
  @Connector("smallrye-in-memory")
  InMemoryConnector broker;

  @Inject
  ObjectMapper json;

  @Inject
  IdempotencyStore idempotencyStore;

  @Inject
  PurgeExpiredIdempotencyKeysUseCase idempotencyPurge;

  @Inject
  UnitOfWork unitOfWork;

  private InMemorySink<String> outbox;

  @BeforeEach
  void setUp() {
    outbox = broker.sink(OutboxRelay.CHANNEL);
    outbox.clear();
    when(risk.assess(any(), any())).thenReturn(new RiskAssessment(true, 12, List.of()));
    when(ledger.debit(any())).thenReturn(new LedgerResult.Applied());
    when(ledger.credit(any())).thenReturn(new LedgerResult.Applied());
    when(ledger.reverse(any(), anyString())).thenReturn(new LedgerResult.Applied());
  }

  @Test
  void ownBankTransferCompletesAndPublishesItsEvents() {
    String transferId = create(UUID.randomUUID().toString(), request("001", "2000000001", "150.00"))
        .statusCode(202)
        .header("Location", notNullValue())
        .body("status", equalTo("PENDING"))
        .body("rail", equalTo("OWN_BANK"))
        .extract().path("transferId");

    awaitStatus(transferId, "COMPLETED")
        .body("saga.steps.step", hasItem("CREDIT"))
        .body("saga.state", equalTo("COMPLETED"));
    await().atMost(TIMEOUT).until(() -> outboxTypes().contains("TransferCompleted"));
  }

  @Test
  void retryWithTheSameKeyReplaysTheOriginalTransfer() {
    String key = UUID.randomUUID().toString();
    String body = request("001", "2000000001", "10.00");

    String first = create(key, body).statusCode(202).extract().path("transferId");
    String second = create(key, body)
        .statusCode(202)
        .header("Idempotent-Replayed", "true")
        .extract().path("transferId");

    assertThat(second).isEqualTo(first);
  }

  @Test
  void sameKeyWithAnotherPayloadIsAConflict() {
    String key = UUID.randomUUID().toString();
    create(key, request("001", "2000000001", "10.00")).statusCode(202);

    create(key, request("001", "2000000001", "99.00"))
        .statusCode(409)
        .contentType("application/problem+json")
        .body("code", equalTo("IDEMPOTENCY_KEY_REUSED"));
  }

  @Test
  void anExpiredKeyStartsANewTransferInsteadOfReplayingOrConflicting() {
    String original = create(UUID.randomUUID().toString(), request("001", "2000000001", "10.00"))
        .statusCode(202).extract().path("transferId");
    String expiredKey = UUID.randomUUID().toString();
    registerExpiredKey(expiredKey, UUID.fromString(original));

    create(expiredKey, request("001", "2000000001", "99.00"))
        .statusCode(202)
        .header("Idempotent-Replayed", nullValue())
        .body("transferId", not(equalTo(original)));
  }

  @Test
  void thePurgeRemovesExpiredKeys() {
    String transferId = create(UUID.randomUUID().toString(), request("001", "2000000001", "10.00"))
        .statusCode(202).extract().path("transferId");
    String expiredKey = UUID.randomUUID().toString();
    registerExpiredKey(expiredKey, UUID.fromString(transferId));

    assertThat(idempotencyPurge.purgeExpired()).isPositive();
    assertThat(unitOfWork.inTransaction(() -> idempotencyStore.findValid(expiredKey, Instant.EPOCH))).isEmpty();
  }

  @Test
  void rejectedCreditIsCompensatedByReversingCreditAndDebit() {
    when(ledger.credit(any())).thenReturn(new LedgerResult.Rejected("ACCOUNT_CLOSED", "Account is closed"));

    String transferId = create(UUID.randomUUID().toString(), request("001", "2000000002", "40.00"))
        .statusCode(202).extract().path("transferId");

    awaitStatus(transferId, "REVERSED").body("statusReason", containsString("ACCOUNT_CLOSED"));
    verify(ledger, times(2)).reverse(any(), anyString());
  }

  @Test
  void transientLedgerFailuresAreRetriedWithBackoff() {
    when(ledger.debit(any()))
        .thenReturn(new LedgerResult.Unavailable("timeout"))
        .thenReturn(new LedgerResult.Applied());

    String transferId = create(UUID.randomUUID().toString(), request("001", "2000000001", "5.00"))
        .statusCode(202).extract().path("transferId");

    awaitStatus(transferId, "COMPLETED").body("saga.steps.outcome", hasItem("RETRY_SCHEDULED"));
  }

  @Test
  void interbankTransferWaitsForTheClearingResult() throws Exception {
    String transferId = create(UUID.randomUUID().toString(), request("010", "3000000001", "75.00"))
        .statusCode(202)
        .body("rail", equalTo("INSTANT"))
        .extract().path("transferId");

    PaymentOrderMessage order = awaitPaymentOrder(UUID.fromString(transferId));
    assertThat(order.clearingSystem().name()).isEqualTo("RPI");

    InMemorySource<String> results = broker.source(PaymentResultConsumer.CHANNEL);
    results.send(json.writeValueAsString(new PaymentResultMessage(order.orderId(), order.transferId(),
        PaymentResultMessage.PaymentStatus.SETTLED, null, null, Instant.now())));

    awaitStatus(transferId, "COMPLETED");
  }

  @Test
  void clearingRejectionReversesTheDebit() throws Exception {
    String transferId = create(UUID.randomUUID().toString(), request("099", "3000000000", "75.00"))
        .statusCode(202)
        .body("rail", equalTo("DEFERRED"))
        .extract().path("transferId");

    PaymentOrderMessage order = awaitPaymentOrder(UUID.fromString(transferId));
    broker.<String>source(PaymentResultConsumer.CHANNEL).send(json.writeValueAsString(new PaymentResultMessage(order.orderId(),
        order.transferId(), PaymentResultMessage.PaymentStatus.REJECTED, "AC01", "Incorrect account number", Instant.now())));

    awaitStatus(transferId, "REVERSED");
  }

  @Test
  void fraudDenialRejectsWithoutTouchingTheLedger() {
    when(risk.assess(any(), any())).thenReturn(new RiskAssessment(false, 95, List.of("BENEFICIARY_BLOCKLISTED")));

    create(UUID.randomUUID().toString(), request("001", "2000000001", "10.00"))
        .statusCode(202)
        .body("status", equalTo("REJECTED"));

    verify(ledger, never()).debit(any());
  }

  @Test
  void unavailableFraudEngineAnswers503SoTheClientCanRetry() {
    when(risk.assess(any(), any())).thenThrow(new DependencyUnavailableException("fraud-engine", "down", null));

    create(UUID.randomUUID().toString(), request("001", "2000000001", "10.00"))
        .statusCode(503)
        .header("Retry-After", notNullValue())
        .body("code", equalTo("DEPENDENCY_UNAVAILABLE"));
  }

  @Test
  void invalidRequestsAreDescribedAsProblemDetails() {
    create(UUID.randomUUID().toString(), request("1", "123", "-5"))
        .statusCode(400)
        .contentType("application/problem+json")
        .body("code", equalTo("VALIDATION_FAILED"))
        .body("violations.field", hasItem("beneficiary.bankCode"))
        .body("correlationId", notNullValue());
  }

  @Test
  void unknownTransferIsNotFound() {
    given().get("/api/v1/transfers/{id}", UUID.randomUUID())
        .then()
        .statusCode(404)
        .body("code", equalTo("RESOURCE_NOT_FOUND"));
  }

  @Test
  void aSafeCorrelationIdIsEchoedAndReachesTheProblemDetails() {
    given().header(CorrelationId.HEADER, "trace-123")
        .get("/api/v1/transfers/{id}", UUID.randomUUID())
        .then()
        .statusCode(404)
        .header(CorrelationId.HEADER, "trace-123")
        .body("correlationId", equalTo("trace-123"));
  }

  @Test
  void anUnsafeCorrelationIdIsReplacedByOneIdUsedEverywhere() {
    ExtractableResponse<Response> response = given().header(CorrelationId.HEADER, "forged	log line")
        .get("/api/v1/transfers/{id}", UUID.randomUUID())
        .then()
        .statusCode(404)
        .header(CorrelationId.HEADER, matchesPattern("^[A-Za-z0-9-]{1,64}$"))
        .extract();

    assertThat(response.header(CorrelationId.HEADER)).isEqualTo(response.path("correlationId"));
  }

  private void registerExpiredKey(String key, UUID transferId) {
    Instant longAgo = Instant.now().minus(Duration.ofDays(2));
    unitOfWork.inTransaction(() ->
        idempotencyStore.register(new IdempotencyRecord(key, "stale-fingerprint", transferId, longAgo), Instant.EPOCH));
  }

  private static ValidatableResponse create(String idempotencyKey, String body) {
    return given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", idempotencyKey)
        .body(body)
        .post("/api/v1/transfers")
        .then();
  }

  private static ValidatableResponse awaitStatus(String transferId, String status) {
    await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200)).until(() ->
        status.equals(given().get("/api/v1/transfers/{id}", transferId).then().extract().path("status")));
    return given().get("/api/v1/transfers/{id}", transferId).then().statusCode(200);
  }

  private PaymentOrderMessage awaitPaymentOrder(UUID transferId) {
    return await().atMost(TIMEOUT).until(() -> outbox.received().stream()
        .filter(message -> Topics.PAYMENT_ORDERS.equals(topicOf(message)))
        .map(message -> read(message.getPayload()))
        .filter(order -> order.transferId().equals(transferId))
        .findFirst()
        .orElse(null), Objects::nonNull);
  }

  private List<String> outboxTypes() {
    return outbox.received().stream()
        .filter(message -> Topics.TRANSFER_EVENTS.equals(topicOf(message)))
        .map(message -> readTree(message.getPayload()).path("type").asText())
        .toList();
  }

  private static String topicOf(Message<String> message) {
    return message.getMetadata(OutgoingKafkaRecordMetadata.class)
        .map(metadata -> ((OutgoingKafkaRecordMetadata<?>) metadata).getTopic())
        .orElse(null);
  }

  private PaymentOrderMessage read(String payload) {
    try {
      return json.readValue(payload, PaymentOrderMessage.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode readTree(String payload) {
    try {
      return json.readTree(payload);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String request(String bankCode, String account, String amount) {
    return """
        {
          "customerId": "C-100",
          "sourceAccount": "1000000001",
          "beneficiary": {"bankCode": "%s", "accountNumber": "%s", "holderName": "Carlos Vega"},
          "amount": %s,
          "currency": "USD",
          "concept": "Integration test"
        }""".formatted(bankCode, account, amount);
  }
}
