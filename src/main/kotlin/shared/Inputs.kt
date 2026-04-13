package shared

fun workflowCallInput(
    description: String,
    type: String,
    required: Boolean,
    default: Any? = null,
): Map<String, Any?> = buildMap {
    put("description", description)
    put("type", type)
    put("required", required)
    if (default != null) put("default", default)
}

fun stringInput(description: String, required: Boolean = false, default: String? = null) =
    workflowCallInput(description, "string", required, default)

fun booleanInput(description: String, required: Boolean = false, default: Boolean? = null) =
    workflowCallInput(description, "boolean", required, default)

fun secretInput(description: String, required: Boolean = true) = mapOf(
    "description" to description,
    "required" to required,
)

val SETUP_ACTION_INPUT = "setup-action" to stringInput(
    description = "Setup action to use: gradle, go",
    required = true,
)

val SETUP_PARAMS_INPUT = "setup-params" to stringInput(
    description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
    default = "{}",
)

val CHECK_COMMAND_INPUT = "check-command" to stringInput(
    description = "Command to run for checking",
    required = true,
)

val APP_ID_SECRET = "app-id" to secretInput("GitHub App ID for generating commit token")
val APP_PRIVATE_KEY_SECRET = "app-private-key" to secretInput("GitHub App private key for generating commit token")

val APP_SECRETS_PASSTHROUGH = mapOf(
    "app-id" to "\${{ secrets.app-id }}",
    "app-private-key" to "\${{ secrets.app-private-key }}",
)

val MAVEN_SONATYPE_SECRETS = mapOf(
    "MAVEN_SONATYPE_USERNAME" to secretInput("Maven Central (Sonatype) username"),
    "MAVEN_SONATYPE_TOKEN" to secretInput("Maven Central (Sonatype) token"),
    "MAVEN_SONATYPE_SIGNING_KEY_ID" to secretInput("GPG signing key ID"),
    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to secretInput("GPG signing public key (ASCII armored)"),
    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to secretInput("GPG signing private key (ASCII armored)"),
    "MAVEN_SONATYPE_SIGNING_PASSWORD" to secretInput("GPG signing key passphrase"),
)

val MAVEN_SONATYPE_SECRETS_PASSTHROUGH = MAVEN_SONATYPE_SECRETS.keys.associateWith { "\${{ secrets.$it }}" }

val GRADLE_PORTAL_SECRETS = mapOf(
    "GRADLE_PUBLISH_KEY" to secretInput("Gradle Plugin Portal publish key"),
    "GRADLE_PUBLISH_SECRET" to secretInput("Gradle Plugin Portal publish secret"),
)

val GRADLE_PORTAL_SECRETS_PASSTHROUGH = GRADLE_PORTAL_SECRETS.keys.associateWith { "\${{ secrets.$it }}" }

fun Map<String, Map<String, Any>>.withRequired(required: Boolean): Map<String, Map<String, Any>> =
    mapValues { (_, v) -> v + ("required" to required) }
