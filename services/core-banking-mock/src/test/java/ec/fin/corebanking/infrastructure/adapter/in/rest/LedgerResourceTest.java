package ec.fin.corebanking.infrastructure.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LedgerResourceTest {

  @Test
  void firstPostingIsCreatedAndTheRetryIsAReplay() {
    String body = posting(UUID.randomUUID(), "1000000001", "1.00");

    given().contentType(ContentType.JSON).body(body).post("/core/v1/ledger/operations").then().statusCode(201);
    given().contentType(ContentType.JSON).body(body).post("/core/v1/ledger/operations")
        .then().statusCode(200).body("status", equalTo("APPLIED"));
  }

  @Test
  void blockedAccountAnswers422WithItsCode() {
    given().contentType(ContentType.JSON).body(posting(UUID.randomUUID(), "1000000003", "1.00"))
        .post("/core/v1/ledger/operations")
        .then()
        .statusCode(422)
        .contentType("application/problem+json")
        .body("code", equalTo("ACCOUNT_BLOCKED"));
  }

  @Test
  void reversingAnUnknownOperationIsHarmless() {
    given().contentType(ContentType.JSON).body("{\"reason\":\"test\"}")
        .post("/core/v1/ledger/operations/{id}/reversal", UUID.randomUUID())
        .then()
        .statusCode(200)
        .body("outcome", equalTo("NOT_FOUND"));
  }

  private static String posting(UUID id, String account, String amount) {
    return """
        {"operationId":"%s","type":"DEBIT","accountNumber":"%s","amount":%s,"currency":"USD","reference":"test"}"""
        .formatted(id, account, amount);
  }
}
