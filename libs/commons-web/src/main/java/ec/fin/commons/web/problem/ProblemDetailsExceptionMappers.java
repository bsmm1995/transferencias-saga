package ec.fin.commons.web.problem;

import com.fasterxml.jackson.core.JacksonException;
import ec.fin.commons.error.BusinessRuleViolationException;
import ec.fin.commons.error.ConflictException;
import ec.fin.commons.error.DependencyUnavailableException;
import ec.fin.commons.error.DomainException;
import ec.fin.commons.error.ResourceNotFoundException;
import ec.fin.commons.web.correlation.CorrelationId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Maps every error to problem details. Stack traces and internal messages never leave the service. */
@Slf4j
public class ProblemDetailsExceptionMappers {

  private static final int UNPROCESSABLE_ENTITY = 422;

  @ServerExceptionMapper
  public Response notFound(ResourceNotFoundException e, UriInfo uri) {
    return fromDomain(Response.Status.NOT_FOUND.getStatusCode(), "Not Found", e, uri);
  }

  @ServerExceptionMapper
  public Response businessRule(BusinessRuleViolationException e, UriInfo uri) {
    return fromDomain(UNPROCESSABLE_ENTITY, "Business rule violated", e, uri);
  }

  @ServerExceptionMapper
  public Response conflict(ConflictException e, UriInfo uri) {
    return fromDomain(Response.Status.CONFLICT.getStatusCode(), "Conflict", e, uri);
  }

  @ServerExceptionMapper
  public Response dependencyUnavailable(DependencyUnavailableException e, UriInfo uri) {
    // The cause stays in the logs (never in the response): without it a 503 cannot be diagnosed.
    log.warn("Dependency {} unavailable: {}", e.getDependency(), e.getMessage(), e);
    return Response.fromResponse(fromDomain(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), "Service Unavailable", e, uri))
        .header("Retry-After", 5)
        .build();
  }

  @ServerExceptionMapper
  public Response validation(ConstraintViolationException e, UriInfo uri) {
    List<ProblemDetail.Violation> violations = e.getConstraintViolations().stream()
        .map(v -> new ProblemDetail.Violation(fieldOf(v), v.getMessage()))
        .sorted(Comparator.comparing(ProblemDetail.Violation::field))
        .toList();
    return build(problem(400, "Invalid request", "The request has invalid fields", "VALIDATION_FAILED", uri)
        .violations(violations)
        .build());
  }

  @ServerExceptionMapper
  public Response malformedBody(JacksonException e, UriInfo uri) {
    return build(problem(400, "Malformed request", "The request body is not valid for this operation", "MALFORMED_REQUEST", uri)
        .build());
  }

  @ServerExceptionMapper
  public Response webApplication(WebApplicationException e, UriInfo uri) {
    int status = e.getResponse().getStatus();
    Response.Status known = Response.Status.fromStatusCode(status);
    String title = known != null ? known.getReasonPhrase() : "HTTP " + status;
    return build(problem(status, title, title, "HTTP_" + status, uri).build());
  }

  @ServerExceptionMapper
  public Response unexpected(Throwable e, UriInfo uri) {
    log.error("Unhandled error on {}", uri.getPath(), e);
    return build(problem(500, "Internal error", "An unexpected error occurred", "INTERNAL_ERROR", uri).build());
  }

  private Response fromDomain(int status, String title, DomainException e, UriInfo uri) {
    return build(problem(status, title, e.getMessage(), e.getCode(), uri).build());
  }

  private ProblemDetail.ProblemDetailBuilder problem(int status, String title, String detail, String code, UriInfo uri) {
    return ProblemDetail.builder()
        .type("urn:problem-type:" + code.toLowerCase(Locale.ROOT).replace('_', '-'))
        .title(title)
        .status(status)
        .detail(detail)
        .instance(uri.getPath())
        .code(code)
        .correlationId(CorrelationId.current().orElse(null));
  }

  private static Response build(ProblemDetail problem) {
    return Response.status(problem.status()).type(ProblemDetail.MEDIA_TYPE).entity(problem).build();
  }

  // Turns "createTransfer.createTransferRequest.beneficiary.bankCode" into "beneficiary.bankCode".
  private static String fieldOf(ConstraintViolation<?> violation) {
    List<String> properties = new ArrayList<>();
    String parameter = null;
    for (Path.Node node : violation.getPropertyPath()) {
      if (node.getKind() == ElementKind.PARAMETER) {
        parameter = node.getName();
      } else if (node.getKind() == ElementKind.PROPERTY || node.getKind() == ElementKind.CONTAINER_ELEMENT) {
        properties.add(node.getName());
      }
    }
    if (!properties.isEmpty()) {
      return String.join(".", properties);
    }
    return parameter != null ? parameter : violation.getPropertyPath().toString();
  }
}
