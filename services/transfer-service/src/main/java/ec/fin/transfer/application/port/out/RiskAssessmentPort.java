package ec.fin.transfer.application.port.out;

import ec.fin.transfer.domain.model.TransferOrder;
import java.util.List;
import java.util.UUID;

/** Driven port: the fraud engine. */
public interface RiskAssessmentPort {

  /**
   * @throws ec.fin.commons.error.DependencyUnavailableException when the fraud engine cannot answer
   */
  RiskAssessment assess(UUID transferId, TransferOrder order);

  record RiskAssessment(boolean approved, int score, List<String> reasons) {
  }
}
