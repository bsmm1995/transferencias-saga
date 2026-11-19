plugins {
    id("saga.quarkus-service-conventions")
}

dependencies {
    implementation(project(":libs:messaging-contracts"))
    implementation("io.quarkus:quarkus-messaging-kafka")

    testImplementation("io.smallrye.reactive:smallrye-reactive-messaging-in-memory")
}
