package ec.fin.corebanking.infrastructure.adapter.in.rest;

import ec.fin.corebanking.infrastructure.adapter.in.rest.api.ChaosApi;
import ec.fin.corebanking.infrastructure.adapter.in.rest.api.model.ChaosSettings;
import ec.fin.corebanking.infrastructure.chaos.ChaosMonkey;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;

/** Driving adapter: implements the generated ChaosApi to change failure injection at runtime. */
@RequiredArgsConstructor
public class ChaosResource implements ChaosApi {

  private final ChaosMonkey chaos;

  @Override
  public Response getChaosSettings() {
    return Response.ok(current()).build();
  }

  @Override
  public Response updateChaosSettings(ChaosSettings settings) {
    chaos.configure(settings.getFailureRate().doubleValue(), ChaosMonkey.Mode.valueOf(settings.getMode().value()));
    return Response.ok(current()).build();
  }

  private ChaosSettings current() {
    return new ChaosSettings()
        .failureRate(BigDecimal.valueOf(chaos.failureRate()))
        .mode(ChaosSettings.ModeEnum.fromValue(chaos.mode().name()));
  }
}
