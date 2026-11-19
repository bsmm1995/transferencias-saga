package ec.fin.transfer.infrastructure.config;

import ec.fin.transfer.application.port.out.EventOutbox;
import ec.fin.transfer.application.port.out.IdempotencyStore;
import ec.fin.transfer.application.port.out.LedgerPort;
import ec.fin.transfer.application.port.out.RiskAssessmentPort;
import ec.fin.transfer.application.port.out.SagaDispatcher;
import ec.fin.transfer.application.port.out.TransferReadModel;
import ec.fin.transfer.application.port.out.TransferRepository;
import ec.fin.transfer.application.port.out.UnitOfWork;
import ec.fin.transfer.application.service.IdempotencyKeyPurgeService;
import ec.fin.transfer.application.service.IdempotencyPolicy;
import ec.fin.transfer.application.service.InitiateTransferService;
import ec.fin.transfer.application.service.SagaOrchestrator;
import ec.fin.transfer.application.service.TransferQueryService;
import ec.fin.transfer.domain.saga.SagaPolicy;
import ec.fin.transfer.domain.service.RailRouter;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

/** Composition root: the only place where framework-free use cases meet their adapters. */
@Dependent
public class UseCaseConfiguration {

  @Produces
  @Singleton
  Clock clock() {
    return Clock.systemUTC();
  }

  @Produces
  @Singleton
  RailRouter railRouter(TransferProperties properties) {
    return RailRouter.standard(properties.rails().ownBankCode(), properties.rails().instantBankCodes());
  }

  @Produces
  @Singleton
  SagaPolicy sagaPolicy(TransferProperties properties) {
    TransferProperties.Saga saga = properties.saga();
    return new SagaPolicy(saga.maxAttempts(), saga.initialBackoff(), saga.backoffMultiplier(), saga.maxBackoff(),
        saga.paymentResultTimeout(), saga.maxPaymentInquiries());
  }

  @Produces
  @Singleton
  IdempotencyPolicy idempotencyPolicy(TransferProperties properties) {
    return new IdempotencyPolicy(properties.idempotency().retention());
  }

  @Produces
  @Singleton
  InitiateTransferService initiateTransfer(TransferRepository transfers, IdempotencyStore idempotency, EventOutbox outbox,
      RiskAssessmentPort risk, RailRouter railRouter, UnitOfWork unitOfWork, SagaDispatcher dispatcher, Clock clock,
      IdempotencyPolicy idempotencyPolicy) {
    return new InitiateTransferService(transfers, idempotency, outbox, risk, railRouter, unitOfWork, dispatcher, clock,
        idempotencyPolicy);
  }

  @Produces
  @Singleton
  IdempotencyKeyPurgeService idempotencyKeyPurge(IdempotencyStore idempotency, UnitOfWork unitOfWork,
      IdempotencyPolicy policy, Clock clock) {
    return new IdempotencyKeyPurgeService(idempotency, unitOfWork, policy, clock);
  }

  @Produces
  @Singleton
  SagaOrchestrator sagaOrchestrator(TransferRepository transfers, EventOutbox outbox, LedgerPort ledger, UnitOfWork unitOfWork,
      SagaDispatcher dispatcher, SagaPolicy policy, Clock clock, TransferProperties properties) {
    return new SagaOrchestrator(transfers, outbox, ledger, unitOfWork, dispatcher, policy, clock, properties.saga().batchSize());
  }

  @Produces
  @Singleton
  TransferQueryService transferQuery(TransferReadModel readModel, UnitOfWork unitOfWork) {
    return new TransferQueryService(readModel, unitOfWork);
  }
}
