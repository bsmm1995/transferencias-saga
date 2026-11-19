package ec.fin.corebanking.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.fin.commons.error.BusinessRuleViolationException;
import ec.fin.commons.error.ConflictException;
import ec.fin.corebanking.application.port.in.LedgerUseCases.PostingCommand;
import ec.fin.corebanking.domain.model.OperationType;
import ec.fin.corebanking.domain.model.ReversalOutcome;
import ec.fin.corebanking.infrastructure.adapter.out.memory.InMemoryAccountRepository;
import ec.fin.corebanking.infrastructure.adapter.out.memory.InMemoryLedgerOperationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerServiceTest {

  private final LedgerService ledger = new LedgerService(new InMemoryAccountRepository(), new InMemoryLedgerOperationRepository(), Clock.systemUTC());

  @Test
  void postingTwiceWithTheSameIdAppliesOnce() {
    PostingCommand debit = debit(UUID.randomUUID(), "1000000001", "100.00");

    assertThat(ledger.post(debit).replayed()).isFalse();
    assertThat(ledger.post(debit).replayed()).isTrue();
    assertThat(ledger.get("1000000001").getBalance()).isEqualByComparingTo("4900.00");
  }

  @Test
  void reusingAnIdWithAnotherPayloadIsAConflict() {
    UUID id = UUID.randomUUID();
    ledger.post(debit(id, "1000000001", "1.00"));

    assertThatThrownBy(() -> ledger.post(debit(id, "1000000001", "2.00"))).isInstanceOf(ConflictException.class);
  }

  @Test
  void insufficientFundsIsABusinessRejection() {
    assertThatThrownBy(() -> ledger.post(debit(UUID.randomUUID(), "1000000002", "500.00")))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "INSUFFICIENT_FUNDS");
  }

  @Test
  void reversalRestoresTheBalanceOnlyOnce() {
    UUID id = UUID.randomUUID();
    ledger.post(debit(id, "1000000001", "50.00"));

    assertThat(ledger.reverse(id, "test")).isEqualTo(ReversalOutcome.REVERSED);
    assertThat(ledger.reverse(id, "test")).isEqualTo(ReversalOutcome.ALREADY_REVERSED);
    assertThat(ledger.get("1000000001").getBalance()).isEqualByComparingTo("5000.00");
  }

  @Test
  void reversalBeforeTheOperationLeavesATombstoneThatBlocksTheLateOperation() {
    UUID id = UUID.randomUUID();

    assertThat(ledger.reverse(id, "compensation arrived first")).isEqualTo(ReversalOutcome.NOT_FOUND);
    assertThatThrownBy(() -> ledger.post(debit(id, "1000000001", "10.00")))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "OPERATION_VOIDED");
  }

  private static PostingCommand debit(UUID id, String account, String amount) {
    return new PostingCommand(id, OperationType.DEBIT, account, new BigDecimal(amount), "USD", "test");
  }
}
