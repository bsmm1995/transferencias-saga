plugins {
    id("saga.quarkus-service-conventions")
}

openApiServer(contract = "fraud-api.yaml", basePackage = "ec.fin.fraud.infrastructure.adapter.in.rest")
