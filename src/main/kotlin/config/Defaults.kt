package config

const val DEFAULT_JAVA_VERSION = "17"
const val DEFAULT_GO_VERSION = "1.22"
const val DEFAULT_PYTHON_VERSION = "3.12"
const val DEFAULT_RELEASE_BRANCHES = "main,[0-9]+\\.x"
const val DEFAULT_CHANGELOG_CONFIG = ".github/changelog-config.json"

private const val MATRIX_FALLBACK_TEMPLATE = """["{0}"]"""

const val JAVA_VERSION_MATRIX_EXPR =
    "\${{ fromJson(inputs.java-versions || format('$MATRIX_FALLBACK_TEMPLATE', inputs.java-version)) }}"
