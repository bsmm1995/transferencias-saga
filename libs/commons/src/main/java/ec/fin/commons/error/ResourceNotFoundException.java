package ec.fin.commons.error;

/** The requested resource does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends DomainException {

  public ResourceNotFoundException(String resource, Object id) {
    super("RESOURCE_NOT_FOUND", "%s '%s' was not found".formatted(resource, id));
  }
}
