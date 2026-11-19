plugins {
    id("saga.quarkus-service-conventions")
}

openApiServer(contract = "core-banking-api.yaml", basePackage = "ec.fin.corebanking.infrastructure.adapter.in.rest")
