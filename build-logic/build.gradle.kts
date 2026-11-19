plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.quarkus.gradle.plugin)
    implementation(libs.openapi.generator.gradle.plugin)
}
