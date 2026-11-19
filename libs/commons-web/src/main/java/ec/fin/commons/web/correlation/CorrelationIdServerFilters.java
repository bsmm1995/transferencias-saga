package ec.fin.commons.web.correlation;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/** Quarkus REST filters that bind the incoming correlation id to the MDC (generating one when absent) and echo it in the response. */
public class CorrelationIdServerFilters {

  /**
   * The resolved id overwrites the incoming header, also when the client sent an unsafe value: the logs, the
   * resource method, downstream calls and the response must all carry one and the same id.
   */
  @ServerRequestFilter(preMatching = true)
  public void bindCorrelationId(ContainerRequestContext request) {
    String correlationId = CorrelationId.resolve(request.getHeaderString(CorrelationId.HEADER));
    request.getHeaders().putSingle(CorrelationId.HEADER, correlationId);
    CorrelationId.bind(correlationId);
  }

  @ServerResponseFilter
  public void echoCorrelationId(ContainerRequestContext request, ContainerResponseContext response) {
    response.getHeaders().putSingle(CorrelationId.HEADER, request.getHeaderString(CorrelationId.HEADER));
    CorrelationId.clear();
  }
}
