package dsl

interface SetupConfigurable {
    var setupAction: String
    var setupParams: String

    companion object {
        const val SETUP_ACTION_DESCRIPTION = "Setup action to use: gradle, go, python"
        const val SETUP_PARAMS_DESCRIPTION = """JSON object with setup parameters (e.g. {"java-version": "21"})"""
        const val SETUP_PARAMS_DEFAULT = "{}"
    }
}
