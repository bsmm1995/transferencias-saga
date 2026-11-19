package ec.fin.transfer.infrastructure.adapter.in.rest;

import ec.fin.commons.web.correlation.CorrelationId;
import ec.fin.transfer.application.port.in.GetTransferUseCase;
import ec.fin.transfer.application.port.in.InitiateTransferUseCase;
import ec.fin.transfer.application.port.in.InitiateTransferUseCase.InitiationResult;
import ec.fin.transfer.infrastructure.adapter.in.rest.api.TransfersApi;
import ec.fin.transfer.infrastructure.adapter.in.rest.api.model.CreateTransferRequest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.UUID;

/**
 * API de Transferencias of the L3 diagram. Implements the interface generated from transfer-api.yaml.
 */
@RunOnVirtualThread
@RequiredArgsConstructor
public class TransferResource implements TransfersApi {

    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final InitiateTransferUseCase initiateTransfer;
    private final GetTransferUseCase getTransfer;

    @Override
    public Response createTransfer(String idempotencyKey, CreateTransferRequest request, String correlationId) {
        InitiationResult result = initiateTransfer.initiate(
                TransferRestMapper.toCommand(idempotencyKey, CorrelationId.resolve(correlationId), request));
        Response.ResponseBuilder response = Response.accepted(TransferRestMapper.toResponse(result.transfer()))
                .location(URI.create("/api/v1/transfers/" + result.transfer().getId()));
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, true);
        }
        return response.build();
    }

    @Override
    public Response getTransfer(UUID transferId) {
        return Response.ok(TransferRestMapper.toDetails(getTransfer.get(transferId))).build();
    }
}
