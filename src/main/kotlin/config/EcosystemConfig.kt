package config

data class EcosystemConfig(
    val tool: SetupTool,
    val checkCommandName: String,
    val checkCommandDescription: String,
    val defaultCheckCommand: String,
    val defaultTagPrefix: String,
)

val GRADLE = EcosystemConfig(
    tool = SetupTool.Gradle,
    checkCommandName = "gradle-command",
    checkCommandDescription = "Gradle check command",
    defaultCheckCommand = "./gradlew check",
    defaultTagPrefix = "",
)

val GO = EcosystemConfig(
    tool = SetupTool.Go,
    checkCommandName = "check-command",
    checkCommandDescription = "Go validation command",
    defaultCheckCommand = "make test",
    defaultTagPrefix = "v",
)
