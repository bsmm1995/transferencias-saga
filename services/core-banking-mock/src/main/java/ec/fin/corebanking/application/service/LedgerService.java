package ec.fin.corebanking.application.service;

import ec.fin.commons.error.BusinessRuleViolationException;
import ec.fin.commons.error.ConflictException;
import ec.fin.commons.error.ResourceNotFoundException;
import ec.fin.corebanking.application.port.in.GetAccountUseCase;
import ec.fin.corebanking.application.port.in.PostLedgerOperationUseCase;
import ec.fin.corebanking.application.port.in.PostLedgerOperationUseCase.PostingCommand;
import ec.fin.corebanking.application.port.in.PostLedgerOperationUseCase.PostingResult;
import ec.fin.corebanking.application.port.in.ReverseLedgerOperationUseCase;
import ec.fin.corebanking.application.port.out.AccountRepository;
import ec.fin.corebanking.application.port.out.LedgerOperationRepository;
import ec.fin.corebanking.domain.model.Account;
import ec.fin.corebanking.domain.model.LedgerOperation;
import ec.fin.corebanking.domain.model.ReversalOutcome;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ledger of the mock core. Methods are synchronized because the in-memory store has no transactions; a real
 * core would serialize postings per account with row locks.
 */
@Slf4j
@RequiredArgsConstructor
public class LedgerService implements PostLedgerOperationUseCase, ReverseLedgerOperationUseCase, GetAccountUseCase {

  private final AccountRepository accounts;
  private final LedgerOperationRepository operations;
  private final Clock clock;

  @Override
  public synchronized PostingResult post(PostingCommand command) {
    Optional<LedgerOperation> existing = operations.find(command.operationId());
    if (existing.isPresent()) {
      return replay(existing.get(), command);
    }
    Account account = accounts.find(command.accountNumber())
        .orElseThrow(() -> new BusinessRuleViolationException("ACCOUNT_NOT_FOUND", "Account %s does not exist".formatted(command.accountNumber())));
    if (!account.getCurrency().equals(command.currency())) {
      throw new BusinessRuleViolationException("CURRENCY_MISMATCH", "Account %s operates in %s".formatted(account.getNumber(), account.getCurrency()));
    }
    switch (command.type()) {
      case DEBIT -> account.debit(command.amount());
      case CREDIT -> account.credit(command.amount());
    }
    LedgerOperation operation = LedgerOperation.applied(command.operationId(), command.type(), command.accountNumber(),
        command.amount(), command.currency(), command.reference(), clock.instant());
    accounts.save(account);
    operations.save(operation);
    log.info("{} {} {} on {} applied, balance {}", command.type(), command.amount(), command.currency(), account.getNumber(), account.getBalance());
    return new PostingResult(operation, false);
  }

  @Override
  public synchronized ReversalOutcome reverse(UUID operationId, String reason) {
    Optional<LedgerOperation> found = operations.find(operationId);
    if (found.isEmpty()) {
      operations.save(LedgerOperation.voided(operationId, clock.instant()));
      log.info("Reversal of unknown operation {} recorded as tombstone ({})", operationId, reason);
      return ReversalOutcome.NOT_FOUND;
    }
    LedgerOperation operation = found.get();
    return switch (operation.getStatus()) {
      case VOIDED -> ReversalOutcome.NOT_FOUND;
      case REVERSED -> ReversalOutcome.ALREADY_REVERSED;
      case APPLIED -> {
        Account account = accounts.find(operation.getAccountNumber()).orElseThrow();
        account.forceAdjust(operation.reversalDelta());
        operation.markReversed(clock.instant());
        accounts.save(account);
        operations.save(operation);
        log.info("Operation {} reversed ({}), balance of {} is {}", operationId, reason, account.getNumber(), account.getBalance());
        yield ReversalOutcome.REVERSED;
      }
    };
  }

  @Override
  public Account get(String accountNumber) {
    return accounts.find(accountNumber).orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));
  }

  private static PostingResult replay(LedgerOperation operation, PostingCommand command) {
    if (operation.getStatus() == LedgerOperation.Status.VOIDED) {
      throw new BusinessRuleViolationException("OPERATION_VOIDED", "Operation %s was reversed before it arrived".formatted(operation.getId()));
    }
    if (!operation.samePayload(command.type(), command.accountNumber(), command.amount(), command.currency())) {
      throw new ConflictException("OPERATION_ID_REUSED", "Operation %s was already used with a different payload".formatted(operation.getId()));
    }
    return new PostingResult(operation, true);
  }
}
