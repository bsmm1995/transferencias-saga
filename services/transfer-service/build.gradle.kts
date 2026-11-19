plugins {
    id("saga.quarkus-service-conventions")
}

openApiServer(contract = "transfer-api.yaml", basePackage = "ec.fin.transfer.infrastructure.adapter.in.rest")
openApiClient(
    name = "coreBankingClient",
    contract = "core-banking-api.yaml",
    basePackage = "ec.fin.transfer.infrastructure.adapter.out.ledger.client",
    configKey = "core-banking",
)
openApiClient(
    name = "fraudClient",
    contract = "fraud-api.yaml",
    basePackage = "ec.fin.transfer.infrastructure.adapter.out.risk.client",
    configKey = "fraud-engine",
)

dependencies {
    implementation(project(":libs:messaging-contracts"))
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-smallrye-fault-tolerance")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-liquibase")
    implementation("io.quarkus:quarkus-messaging-kafka")
    implementation("io.quarkus:quarkus-scheduler")

    testImplementation("io.quarkus:quarkus-junit-mockito")
    testImplementation("io.smallrye.reactive:smallrye-reactive-messaging-in-memory")
    testImplementation("org.awaitility:awaitility")
}
