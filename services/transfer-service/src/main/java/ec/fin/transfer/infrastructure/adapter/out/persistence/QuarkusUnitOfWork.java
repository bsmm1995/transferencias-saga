package ec.fin.transfer.infrastructure.adapter.out.persistence;

import ec.fin.transfer.application.port.out.UnitOfWork;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/** Driven adapter: the UnitOfWork port on top of Narayana JTA. Joins an existing transaction or starts one. */
@ApplicationScoped
public class QuarkusUnitOfWork implements UnitOfWork {

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return QuarkusTransaction.joiningExisting().call(work::get);
  }
}
