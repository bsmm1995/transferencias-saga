package ec.fin.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.fin.transfer.domain.event.TransferEvent;
import ec.fin.transfer.domain.saga.SagaCommand;
import ec.fin.transfer.domain.saga.SagaPolicy;
import ec.fin.transfer.domain.saga.SagaState;
import ec.fin.transfer.domain.saga.SagaStepLog;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The saga state machine is pure Java, so every branch is tested without containers or mocks. */
class TransferSagaTest {

  private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
  private static final SagaPolicy POLICY = new SagaPolicy(3, Duration.ofSeconds(1), 2, Duration.ofSeconds(30), Duration.ofSeconds(15), 2);

  @Nested
  @DisplayName("own bank transfer")
  class OwnBank {

    @Test
    void debitThenCreditCompletesTheTransfer() {
      Transfer transfer = accepted(Rail.OWN_BANK);
      assertThat(transfer.nextCommand()).isInstanceOf(SagaCommand.Debit.class);

      transfer.debitSucceeded(NOW, POLICY);
      assertThat(transfer.getState()).isEqualTo(SagaState.CREDIT_PENDING);
      assertThat(transfer.isDue(NOW)).isTrue();
      assertThat(transfer.nextCommand()).isInstanceOf(SagaCommand.Credit.class);

      transfer.creditSucceeded(NOW);
      assertThat(transfer.status()).isEqualTo(TransferStatus.COMPLETED);
      assertThat(transfer.isDue(NOW)).isFalse();
      assertThat(eventTypes(transfer)).containsExactly(
          TransferEvent.Accepted.class, TransferEvent.FundsDebited.class, TransferEvent.Completed.class);
    }

    @Test
    void rejectedCreditCompensatesCreditAndDebitInReverseOrder() {
      Transfer transfer = accepted(Rail.OWN_BANK);
      transfer.debitSucceeded(NOW, POLICY);

      transfer.creditRejected("ACCOUNT_CLOSED", NOW);

      assertThat(transfer.getState()).isEqualTo(SagaState.COMPENSATING);
      SagaCommand.Compensate compensate = (SagaCommand.Compensate) transfer.nextCommand();
      assertThat(compensate.operationIds()).containsExactly(transfer.creditOperationId(), transfer.debitOperationId());

      transfer.compensationSucceeded(NOW);
      assertThat(transfer.status()).isEqualTo(TransferStatus.REVERSED);
      assertThat(transfer.getStatusReason()).contains("ACCOUNT_CLOSED");
    }
  }

  @Nested
  @DisplayName("interbank transfer")
  class Interbank {

    @Test
    void debitRequestsAPaymentOrderAndWaitsForTheResult() {
      Transfer transfer = accepted(Rail.INSTANT);
      transfer.debitSucceeded(NOW, POLICY);

      assertThat(transfer.getState()).isEqualTo(SagaState.AWAITING_PAYMENT_RESULT);
      assertThat(transfer.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(15));
      assertThat(transfer.pullEvents()).anySatisfy(event -> assertThat(event)
          .isInstanceOfSatisfying(TransferEvent.PaymentOrderRequested.class,
              order -> assertThat(order.orderId()).isEqualTo(transfer.paymentOrderId())));
    }

    @Test
    void settledPaymentCompletesAndDuplicatesAreIgnored() {
      Transfer transfer = awaitingPayment();

      assertThat(transfer.paymentSettled(transfer.paymentOrderId(), NOW)).isTrue();
      assertThat(transfer.paymentSettled(transfer.paymentOrderId(), NOW)).isFalse();
      assertThat(transfer.status()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    void resultForAnotherOrderIsIgnored() {
      Transfer transfer = awaitingPayment();

      assertThat(transfer.paymentSettled(UUID.randomUUID(), NOW)).isFalse();
      assertThat(transfer.getState()).isEqualTo(SagaState.AWAITING_PAYMENT_RESULT);
    }

    @Test
    void rejectedPaymentCompensatesOnlyTheDebit() {
      Transfer transfer = awaitingPayment();

      transfer.paymentRejected(transfer.paymentOrderId(), "AC01", NOW);

      SagaCommand.Compensate compensate = (SagaCommand.Compensate) transfer.nextCommand();
      assertThat(compensate.operationIds()).containsExactly(transfer.debitOperationId());
    }

    @Test
    void timeoutResendsTheSameOrderAndFinallyEscalatesInsteadOfCompensating() {
      Transfer transfer = awaitingPayment();
      transfer.pullEvents();

      transfer.paymentResultTimedOut(NOW, POLICY);
      transfer.paymentResultTimedOut(NOW, POLICY);
      assertThat(transfer.pullEvents()).filteredOn(TransferEvent.PaymentOrderRequested.class::isInstance).hasSize(2);
      assertThat(transfer.getState()).isEqualTo(SagaState.AWAITING_PAYMENT_RESULT);

      transfer.paymentResultTimedOut(NOW, POLICY);
      assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
      assertThat(transfer.getStatusReason()).contains("manual reconciliation");
    }
  }

  @Nested
  @DisplayName("failures")
  class Failures {

    @Test
    void businessRejectionOfTheDebitEndsWithoutCompensation() {
      Transfer transfer = accepted(Rail.OWN_BANK);

      transfer.debitRejected("INSUFFICIENT_FUNDS", NOW);

      assertThat(transfer.status()).isEqualTo(TransferStatus.REJECTED);
      assertThat(transfer.isDue(NOW)).isFalse();
    }

    @Test
    void transientFailuresBackOffExponentially() {
      Transfer transfer = accepted(Rail.OWN_BANK);

      transfer.stepFailedTransiently("timeout", NOW, POLICY);
      assertThat(transfer.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
      transfer.stepFailedTransiently("timeout", NOW, POLICY);
      assertThat(transfer.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
      assertThat(transfer.getState()).isEqualTo(SagaState.DEBIT_PENDING);
    }

    @Test
    void exhaustedDebitIsCompensatedBecauseItsOutcomeIsUnknown() {
      Transfer transfer = accepted(Rail.OWN_BANK);

      exhaust(transfer);

      assertThat(transfer.getState()).isEqualTo(SagaState.COMPENSATING);
      assertThat(transfer.getStatusReason()).contains("DEBIT outcome unknown");
    }

    @Test
    void exhaustedCompensationEscalatesToFailed() {
      Transfer transfer = accepted(Rail.OWN_BANK);
      transfer.debitSucceeded(NOW, POLICY);
      transfer.creditRejected("ACCOUNT_CLOSED", NOW);

      exhaust(transfer);

      assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    void transitionsFromTheWrongStateAreRefused() {
      Transfer transfer = accepted(Rail.OWN_BANK);

      assertThatThrownBy(() -> transfer.creditSucceeded(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void riskRejectionNeverStartsTheSaga() {
      Transfer transfer = Transfer.rejectByRisk(UUID.randomUUID(), order("002"), Rail.INSTANT, 95, "BLOCKLISTED", "cid", NOW);

      assertThat(transfer.status()).isEqualTo(TransferStatus.REJECTED);
      assertThat(transfer.isDue(NOW)).isFalse();
      assertThat(transfer.pullStepLogs()).extracting(SagaStepLog::outcome).containsExactly(SagaStepLog.Outcome.REJECTED);
    }

    private void exhaust(Transfer transfer) {
      for (int attempt = 0; attempt < POLICY.maxAttempts(); attempt++) {
        transfer.stepFailedTransiently("timeout", NOW, POLICY);
      }
    }
  }

  @Test
  void operationIdsAreStableSoRetriesAreIdempotent() {
    Transfer transfer = accepted(Rail.OWN_BANK);

    assertThat(transfer.debitOperationId()).isEqualTo(transfer.debitOperationId());
    assertThat(transfer.debitOperationId()).isNotEqualTo(transfer.creditOperationId());
  }

  private static Transfer accepted(Rail rail) {
    return Transfer.accept(UUID.randomUUID(), order(rail == Rail.OWN_BANK ? "001" : "010"), rail, 10, "cid", NOW);
  }

  private static Transfer awaitingPayment() {
    Transfer transfer = accepted(Rail.INSTANT);
    transfer.debitSucceeded(NOW, POLICY);
    return transfer;
  }

  private static TransferOrder order(String bankCode) {
    return new TransferOrder("C-1", "1000000001", new Beneficiary(bankCode, "2000000001", "Carlos Vega"),
        Money.of(new BigDecimal("100.00"), "USD"), "Rent");
  }

  private static List<Class<?>> eventTypes(Transfer transfer) {
    return transfer.pullEvents().stream().<Class<?>>map(Object::getClass).toList();
  }
}
