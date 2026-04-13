package config

sealed class SetupTool(
    val actionName: String,
    val versionKey: String,
    val defaultVersion: String,
) {
    val id: String get() = actionName.removePrefix("setup-")

    abstract fun toParamsJson(versionExpr: String): String

    data object Gradle : SetupTool("setup-gradle", "java-version", DEFAULT_JAVA_VERSION) {
        override fun toParamsJson(versionExpr: String) =
            """{"java-version": "$versionExpr"}"""
    }

    data object Go : SetupTool("setup-go", "go-version", DEFAULT_GO_VERSION) {
        override fun toParamsJson(versionExpr: String) =
            """{"go-version": "$versionExpr"}"""
    }

    data object Python : SetupTool("setup-python", "python-version", DEFAULT_PYTHON_VERSION) {
        override fun toParamsJson(versionExpr: String) =
            """{"python-version": "$versionExpr"}"""
    }
}
