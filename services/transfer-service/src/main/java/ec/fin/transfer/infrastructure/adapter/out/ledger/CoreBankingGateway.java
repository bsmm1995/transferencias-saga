package ec.fin.transfer.infrastructure.adapter.out.ledger;

import ec.fin.transfer.infrastructure.adapter.out.ledger.client.api.ApiException;
import ec.fin.transfer.infrastructure.adapter.out.ledger.client.api.LedgerApi;
import ec.fin.transfer.infrastructure.adapter.out.ledger.client.api.model.LedgerOperationRequest;
import ec.fin.transfer.infrastructure.adapter.out.ledger.client.api.model.Problem;
import ec.fin.transfer.infrastructure.adapter.out.ledger.client.api.model.ReversalRequest;
import ec.fin.transfer.infrastructure.adapter.out.ledger.client.api.model.ReversalResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Resilience boundary around the generated client. Retries are deliberately absent: the saga retries with a
 * persisted backoff, which survives restarts. Business rejections do not count as failures for the breaker.
 */
@ApplicationScoped
public class CoreBankingGateway {

  private static final int BAD_REQUEST = 400;
  private static final int REQUEST_TIMEOUT = 408;
  private static final int UNPROCESSABLE_ENTITY = 422;
  private static final int TOO_MANY_REQUESTS = 429;
  private static final int INTERNAL_SERVER_ERROR = 500;

  private final LedgerApi ledgerApi;

  public CoreBankingGateway(@RestClient LedgerApi ledgerApi) {
    this.ledgerApi = ledgerApi;
  }

  @Timeout(value = 3, unit = ChronoUnit.SECONDS)
  @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10, delayUnit = ChronoUnit.SECONDS,
      skipOn = LedgerRejectedException.class)
  public void post(LedgerOperationRequest request) {
    try {
      ledgerApi.postLedgerOperation(request);
    } catch (ApiException e) {
      throw translate(e);
    }
  }

  @Timeout(value = 3, unit = ChronoUnit.SECONDS)
  @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10, delayUnit = ChronoUnit.SECONDS,
      skipOn = LedgerRejectedException.class)
  public ReversalResult reverse(UUID operationId, String reason) {
    try {
      return ledgerApi.reverseLedgerOperation(operationId, new ReversalRequest().reason(reason));
    } catch (ApiException e) {
      throw translate(e);
    }
  }

  /**
   * A 4xx is a definitive answer (422 business rule, 409 operation id reused, 400 invalid posting): retrying the
   * same idempotent request would get the same answer. 408 and 429 are the exceptions, they mean "try later".
   */
  private static RuntimeException translate(ApiException e) {
    int status = e.getResponse().getStatus();
    if (isDefinitiveRejection(status)) {
      Problem problem = readProblem(e, status);
      return new LedgerRejectedException(problem.getCode(), problem.getDetail());
    }
    return new IllegalStateException("Core banking answered HTTP " + status, e);
  }

  static boolean isDefinitiveRejection(int status) {
    return status >= BAD_REQUEST && status < INTERNAL_SERVER_ERROR && status != REQUEST_TIMEOUT && status != TOO_MANY_REQUESTS;
  }

  private static Problem readProblem(ApiException e, int status) {
    Problem fallback = new Problem().code(status == UNPROCESSABLE_ENTITY ? "REJECTED" : "HTTP_" + status);
    try {
      Problem problem = e.getResponse().readEntity(Problem.class);
      return problem != null && problem.getCode() != null ? problem : fallback;
    } catch (RuntimeException unreadable) {
      return fallback;
    }
  }
}
