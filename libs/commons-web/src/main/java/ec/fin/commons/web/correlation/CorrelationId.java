package ec.fin.commons.web.correlation;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jboss.logging.MDC;

/** Correlation id carried through HTTP headers, Kafka headers and the logging MDC. */
public final class CorrelationId {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  // Only safe values reach the logs, which prevents log forging through the header.
  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

  private CorrelationId() {
  }

  public static String resolve(String candidate) {
    return candidate != null && SAFE.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
  }

  public static Optional<String> current() {
    return Optional.ofNullable(MDC.get(MDC_KEY)).map(Object::toString);
  }

  public static void bind(String correlationId) {
    MDC.put(MDC_KEY, resolve(correlationId));
  }

  public static void clear() {
    MDC.remove(MDC_KEY);
  }
}
