package ec.fin.transfer.application.service;

import ec.fin.transfer.application.port.in.InitiateTransferUseCase.InitiateTransferCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Stream;

/** SHA-256 of the business payload. Detects an idempotency key reused with a different request. */
final class RequestFingerprint {

  private RequestFingerprint() {
  }

  static String of(InitiateTransferCommand command) {
    String canonical = String.join("|", Stream.of(
            command.customerId(),
            command.sourceAccount(),
            command.beneficiaryBankCode(),
            command.beneficiaryAccount(),
            command.beneficiaryName(),
            command.amount() == null ? null : command.amount().stripTrailingZeros().toPlainString(),
            command.currency(),
            command.concept())
        .map(value -> Objects.toString(value, ""))
        .toList());
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
