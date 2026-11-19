import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.jvm.tasks.ProcessResources
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

/**
 * API-first helpers. Contracts live in `contracts/openapi` at the repository root and are the single
 * source of truth: providers generate their JAX-RS interfaces, consumers generate MicroProfile REST clients.
 */
private val commonOptions = mapOf(
    "useJakartaEe" to "true",
    "dateLibrary" to "java8",
    "openApiNullable" to "false",
    "hideGenerationTimestamp" to "true",
    "sourceFolder" to "src/main/java",
)

/** Generates the server-side interfaces and models a service implements, and serves the contract as its OpenAPI document. */
fun Project.openApiServer(contract: String, basePackage: String) {
    registerGeneration("server", contract) {
        generatorName.set("jaxrs-spec")
        apiPackage.set("$basePackage.api")
        modelPackage.set("$basePackage.api.model")
        configOptions.set(
            commonOptions + mapOf(
                "interfaceOnly" to "true",
                "returnResponse" to "true",
                "useTags" to "true",
                "useSwaggerAnnotations" to "false",
                "useMicroProfileOpenAPIAnnotations" to "false",
                "useBeanValidation" to "true",
                "generateBuilders" to "false",
                "supportAsync" to "false",
            ),
        )
    }
    tasks.named<ProcessResources>("processResources") {
        from(rootProject.file("contracts/openapi/$contract")) {
            into("META-INF")
            rename { "openapi.yaml" }
        }
    }
}

/** Generates a typed MicroProfile REST client for a contract owned by another service. */
fun Project.openApiClient(name: String, contract: String, basePackage: String, configKey: String) {
    registerGeneration(name, contract) {
        generatorName.set("java")
        library.set("microprofile")
        apiPackage.set("$basePackage.api")
        modelPackage.set("$basePackage.api.model")
        invokerPackage.set(basePackage)
        configOptions.set(
            commonOptions + mapOf(
                "microprofileRestClientVersion" to "3.0",
                "serializationLibrary" to "jackson",
                "useBeanValidation" to "false",
                "configKey" to configKey,
            ),
        )
    }
}

private fun Project.registerGeneration(name: String, contract: String, configure: GenerateTask.() -> Unit) {
    val output = layout.buildDirectory.dir("generated/openapi/$name")
    val taskName = "openApiGenerate" + name.replaceFirstChar { it.uppercase() }
    val generate = tasks.register<GenerateTask>(taskName) {
        group = "openapi"
        inputSpec.set(rootProject.file("contracts/openapi/$contract").absolutePath)
        outputDir.set(output)
        cleanupOutput.set(true)
        generateApiTests.set(false)
        generateModelTests.set(false)
        generateApiDocumentation.set(false)
        generateModelDocumentation.set(false)
        configure()
    }
    extensions.getByType<JavaPluginExtension>().sourceSets.named("main") {
        java.srcDir(files(output.map { it.dir("src/main/java") }).builtBy(generate))
    }
}
