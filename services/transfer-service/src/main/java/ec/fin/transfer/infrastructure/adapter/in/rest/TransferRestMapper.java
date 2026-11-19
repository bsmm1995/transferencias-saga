package ec.fin.transfer.infrastructure.adapter.in.rest;

import ec.fin.transfer.application.port.in.GetTransferUseCase.TransferView;
import ec.fin.transfer.application.port.in.InitiateTransferUseCase.InitiateTransferCommand;
import ec.fin.transfer.domain.model.Transfer;
import ec.fin.transfer.domain.model.TransferOrder;
import ec.fin.transfer.domain.saga.SagaStepLog;
import ec.fin.transfer.infrastructure.adapter.in.rest.api.model.Beneficiary;
import ec.fin.transfer.infrastructure.adapter.in.rest.api.model.CreateTransferRequest;
import ec.fin.transfer.infrastructure.adapter.in.rest.api.model.Rail;
import ec.fin.transfer.infrastructure.adapter.in.rest.api.model.SagaProgress;
import ec.fin.transfer.infrastructure.adapter.in.rest.api.model.SagaStep;
import ec.fin.transfer.infrastructure.adapter.in.rest.api.model.TransferDetails;
import ec.fin.transfer.infrastructure.adapter.in.rest.api.model.TransferResponse;
import ec.fin.transfer.infrastructure.adapter.in.rest.api.model.TransferStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Translates between the generated API models and the use case boundary.
 */
final class TransferRestMapper {

    private TransferRestMapper() {
    }

    static InitiateTransferCommand toCommand(String idempotencyKey, String correlationId, CreateTransferRequest request) {
        return new InitiateTransferCommand(
                idempotencyKey,
                correlationId,
                request.getCustomerId(),
                request.getSourceAccount(),
                request.getBeneficiary().getBankCode(),
                request.getBeneficiary().getAccountNumber(),
                request.getBeneficiary().getHolderName(),
                request.getAmount(),
                request.getCurrency().value(),
                request.getConcept());
    }

    static TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse()
                .transferId(transfer.getId())
                .status(TransferStatus.fromValue(transfer.status().name()))
                .rail(Rail.fromValue(transfer.getRail().name()))
                .statusReason(transfer.getStatusReason())
                .createdAt(toOffset(transfer.getCreatedAt()));
    }

    static TransferDetails toDetails(TransferView view) {
        Transfer transfer = view.transfer();
        TransferOrder order = transfer.getOrder();
        SagaProgress saga = new SagaProgress()
                .state(transfer.getState().name())
                .attempts(transfer.getAttempts())
                .nextAttemptAt(toOffset(transfer.getNextAttemptAt()))
                .steps(view.journal().stream().map(TransferRestMapper::toStep).toList());
        return new TransferDetails()
                .transferId(transfer.getId())
                .status(TransferStatus.fromValue(transfer.status().name()))
                .rail(Rail.fromValue(transfer.getRail().name()))
                .statusReason(transfer.getStatusReason())
                .customerId(order.customerId())
                .sourceAccount(order.sourceAccount())
                .beneficiary(new Beneficiary()
                        .bankCode(order.beneficiary().bankCode())
                        .accountNumber(order.beneficiary().accountNumber())
                        .holderName(order.beneficiary().holderName()))
                .amount(order.money().amount())
                .currency(order.money().currency().name())
                .concept(order.concept())
                .riskScore(transfer.getRiskScore())
                .createdAt(toOffset(transfer.getCreatedAt()))
                .updatedAt(toOffset(transfer.getUpdatedAt()))
                .saga(saga);
    }

    private static SagaStep toStep(SagaStepLog log) {
        return new SagaStep()
                .step(log.step().name())
                .outcome(log.outcome().name())
                .attempt(log.attempt())
                .detail(log.detail())
                .occurredAt(toOffset(log.occurredAt()));
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
