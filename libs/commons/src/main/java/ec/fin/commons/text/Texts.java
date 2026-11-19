package ec.fin.commons.text;

/** Small, framework-free text helpers shared by every module. */
public final class Texts {

  private Texts() {
  }

  /** Cuts a value to fit a bounded column or field; {@code null} stays {@code null}. */
  public static String truncate(String value, int maxLength) {
    if (maxLength < 0) {
      throw new IllegalArgumentException("maxLength must not be negative");
    }
    return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
