package ec.fin.commons.web.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;

/** RFC 9457 problem details, extended with a stable error code and the correlation id. */
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProblemDetail(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    String code,
    String correlationId,
    List<Violation> violations) {

  public static final String MEDIA_TYPE = "application/problem+json";

  public record Violation(String field, String message) {
  }
}
