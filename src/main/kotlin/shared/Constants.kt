package shared

const val DEFAULT_JAVA_VERSION = "17"
const val DEFAULT_GO_VERSION = "1.22"
const val DEFAULT_PYTHON_VERSION = "3.12"
const val DEFAULT_RELEASE_BRANCHES = "main,[0-9]+\\.x"
const val DEFAULT_CHANGELOG_CONFIG = ".github/changelog-config.json"

val JAVA_VERSION_MATRIX_EXPR =
    "\${{ fromJson(inputs.java-versions || format('[\"" + "{0}" + "\"]', inputs.java-version)) }}"

const val WORKFLOW_REF = "v2"
const val WORKFLOW_BASE = "zenhelix/ci-workflows/.github/workflows"
const val ACTION_BASE = "zenhelix/ci-workflows/.github/actions"

fun reusableWorkflow(name: String) = "$WORKFLOW_BASE/$name@$WORKFLOW_REF"
fun localAction(name: String) = "$ACTION_BASE/$name@$WORKFLOW_REF"
