package ec.fin.corebanking.infrastructure.adapter.in.rest;

import ec.fin.corebanking.application.port.in.PostLedgerOperationUseCase;
import ec.fin.corebanking.application.port.in.PostLedgerOperationUseCase.PostingCommand;
import ec.fin.corebanking.application.port.in.PostLedgerOperationUseCase.PostingResult;
import ec.fin.corebanking.application.port.in.ReverseLedgerOperationUseCase;
import ec.fin.corebanking.domain.model.LedgerOperation;
import ec.fin.corebanking.domain.model.OperationType;
import ec.fin.corebanking.domain.model.ReversalOutcome;
import ec.fin.corebanking.infrastructure.adapter.in.rest.api.LedgerApi;
import ec.fin.corebanking.infrastructure.adapter.in.rest.api.model.LedgerOperationRequest;
import ec.fin.corebanking.infrastructure.adapter.in.rest.api.model.ReversalRequest;
import ec.fin.corebanking.infrastructure.adapter.in.rest.api.model.ReversalResult;
import ec.fin.corebanking.infrastructure.chaos.ChaosMonkey;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.core.Response;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Driving adapter: implements the generated LedgerApi. Answers 201 for a new posting and 200 for an idempotent replay. */
@RunOnVirtualThread
@RequiredArgsConstructor
public class LedgerResource implements LedgerApi {

  private final PostLedgerOperationUseCase postOperation;
  private final ReverseLedgerOperationUseCase reverseOperation;
  private final ChaosMonkey chaos;

  @Override
  public Response postLedgerOperation(LedgerOperationRequest request) {
    chaos.maybeFailBeforeApply();
    PostingResult result = postOperation.post(new PostingCommand(
        request.getOperationId(),
        OperationType.valueOf(request.getType().toString()),
        request.getAccountNumber(),
        request.getAmount(),
        request.getCurrency().value(),
        request.getReference()));
    chaos.maybeFailAfterApply();
    Response.Status status = result.replayed() ? Response.Status.OK : Response.Status.CREATED;
    return Response.status(status).entity(toModel(result.operation())).build();
  }

  @Override
  public Response reverseLedgerOperation(UUID operationId, ReversalRequest request) {
    ReversalOutcome outcome = reverseOperation.reverse(operationId, request.getReason());
    return Response.ok(new ReversalResult()
            .operationId(operationId)
            .outcome(ReversalResult.OutcomeEnum.fromValue(outcome.name())))
        .build();
  }

  private static ec.fin.corebanking.infrastructure.adapter.in.rest.api.model.LedgerOperation toModel(LedgerOperation operation) {
    return new ec.fin.corebanking.infrastructure.adapter.in.rest.api.model.LedgerOperation()
        .operationId(operation.getId())
        .type(ec.fin.corebanking.infrastructure.adapter.in.rest.api.model.OperationType.fromValue(operation.getType().name()))
        .accountNumber(operation.getAccountNumber())
        .amount(operation.getAmount())
        .currency(operation.getCurrency())
        .status(ec.fin.corebanking.infrastructure.adapter.in.rest.api.model.LedgerOperation.StatusEnum.fromValue(operation.getStatus().name()))
        .appliedAt(operation.getAppliedAt().atOffset(ZoneOffset.UTC));
  }
}
