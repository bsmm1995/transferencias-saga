package ec.fin.commons.web.correlation;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

/** Register on REST clients with {@code quarkus.rest-client.<key>.providers} to propagate the correlation id downstream. */
public class CorrelationIdClientFilter implements ClientRequestFilter {

  @Override
  public void filter(ClientRequestContext request) {
    CorrelationId.current().ifPresent(id -> request.getHeaders().putSingle(CorrelationId.HEADER, id));
  }
}
