pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "transfer-saga"

include(
    "libs:commons",
    "libs:commons-web",
    "libs:messaging-contracts",
    "libs:architecture-rules",
    "services:transfer-service",
    "services:core-banking-mock",
    "services:fraud-mock",
    "services:payment-connector-mock",
)
