package config

import dsl.core.MatrixRefExpr

sealed class SetupTool(
    val actionName: String,
    val versionKey: String,
    val defaultVersion: String,
    val versionDescription: String,
) {
    val id: String = actionName.removePrefix("setup-")

    fun toParamsJson(versionExpr: String): String =
        """{"$versionKey": "$versionExpr"}"""

    fun toParamsJson(versionExpr: MatrixRefExpr): String =
        """{"$versionKey": "${versionExpr.expression}"}"""

    data object Gradle : SetupTool("setup-gradle", "java-version", DEFAULT_JAVA_VERSION, "JDK version to use")
    data object Go : SetupTool("setup-go", "go-version", DEFAULT_GO_VERSION, "Go version to use")
    data object Python : SetupTool("setup-python", "python-version", DEFAULT_PYTHON_VERSION, "Python version to use")

    companion object {
        val entries: List<SetupTool> by lazy { listOf(Gradle, Go, Python) }
    }
}
