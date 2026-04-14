package config

data class ToolTagConfig(
    val tool: SetupTool,
    val commandInputName: String,
    val commandDescription: String,
    val defaultCommand: String,
    val defaultTagPrefix: String,
)

val GRADLE_TAG = ToolTagConfig(
    tool = SetupTool.Gradle,
    commandInputName = "gradle-command",
    commandDescription = "Gradle check command",
    defaultCommand = "./gradlew check",
    defaultTagPrefix = "",
)

val GO_TAG = ToolTagConfig(
    tool = SetupTool.Go,
    commandInputName = "check-command",
    commandDescription = "Go validation command",
    defaultCommand = "make test",
    defaultTagPrefix = "v",
)
