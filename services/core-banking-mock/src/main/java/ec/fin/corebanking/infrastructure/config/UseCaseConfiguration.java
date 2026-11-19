package ec.fin.corebanking.infrastructure.config;

import ec.fin.corebanking.application.port.out.AccountRepository;
import ec.fin.corebanking.application.port.out.LedgerOperationRepository;
import ec.fin.corebanking.application.service.LedgerService;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

/** Composition root: builds the framework-free use cases with their adapters. */
@Dependent
public class UseCaseConfiguration {

  @Produces
  @Singleton
  LedgerService ledgerService(AccountRepository accounts, LedgerOperationRepository operations) {
    return new LedgerService(accounts, operations, Clock.systemUTC());
  }
}
