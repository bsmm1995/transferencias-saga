package ec.fin.commons.error;

import lombok.Getter;

/** A downstream system could not answer; the caller may retry later with the same request. */
@Getter
public class DependencyUnavailableException extends DomainException {

  private final String dependency;

  public DependencyUnavailableException(String dependency, String message, Throwable cause) {
    super("DEPENDENCY_UNAVAILABLE", message, cause);
    this.dependency = dependency;
  }
}
