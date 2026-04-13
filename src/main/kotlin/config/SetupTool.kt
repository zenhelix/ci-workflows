package config

sealed class SetupTool(
    val actionName: String,
    val versionKey: String,
    val defaultVersion: String,
) {
    val id: String get() = actionName.removePrefix("setup-")

    fun toParamsJson(versionExpr: String): String =
        """{"$versionKey": "$versionExpr"}"""

    data object Gradle : SetupTool("setup-gradle", "java-version", DEFAULT_JAVA_VERSION)
    data object Go : SetupTool("setup-go", "go-version", DEFAULT_GO_VERSION)
    data object Python : SetupTool("setup-python", "python-version", DEFAULT_PYTHON_VERSION)
}
