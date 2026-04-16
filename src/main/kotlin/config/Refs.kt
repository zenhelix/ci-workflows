package config

const val WORKFLOW_REF = "v2"
const val WORKFLOW_BASE = "zenhelix/ci-workflows/.github/workflows"
const val ACTION_BASE = "zenhelix/ci-workflows/.github/actions"

fun reusableWorkflow(name: String) = "$WORKFLOW_BASE/$name@$WORKFLOW_REF"
fun localAction(name: String) = "$ACTION_BASE/$name@$WORKFLOW_REF"
