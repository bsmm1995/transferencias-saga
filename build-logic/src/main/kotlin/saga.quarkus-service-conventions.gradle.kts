plugins {
    id("saga.java-conventions")
    id("io.quarkus")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(enforcedPlatform(libs.findLibrary("quarkus-bom").get()))
    implementation(project(":libs:commons"))
    implementation(project(":libs:commons-web"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-smallrye-openapi")

    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation(project(":libs:architecture-rules"))
}

tasks.withType<Test>().configureEach {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

