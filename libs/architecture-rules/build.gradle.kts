plugins {
    id("saga.java-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    api(libs.findLibrary("archunit-junit5").get())
}
