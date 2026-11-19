plugins {
    id("saga.java-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":libs:commons"))
    implementation(platform(libs.findLibrary("quarkus-bom").get()))
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-hibernate-validator")
}
