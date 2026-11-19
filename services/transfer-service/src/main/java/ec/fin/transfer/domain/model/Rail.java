package ec.fin.transfer.domain.model;

/**
 * Settlement rail chosen by the {@link ec.fin.transfer.domain.service.RailRouter}. Rail-specific behaviour lives
 * here so the saga asks the rail instead of comparing against constants.
 */
public enum Rail {
  /** Both accounts live in our core: debit and credit are local postings. */
  OWN_BANK(true),
  /** Central bank instant payments network (ISO 20022), settles in seconds. */
  INSTANT(false),
  /** Central bank SPI, settles in deferred batches. */
  DEFERRED(false);

  private final boolean settlesInCore;

  Rail(boolean settlesInCore) {
    this.settlesInCore = settlesInCore;
  }

  /** True when the credit leg is a local posting; false when it goes out to a clearing network. */
  public boolean settlesInCore() {
    return settlesInCore;
  }
}
