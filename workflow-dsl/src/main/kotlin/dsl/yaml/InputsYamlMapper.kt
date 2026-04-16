package dsl.yaml

import dsl.core.InputDefault
import dsl.core.WorkflowInputDef

fun toInputsYaml(
    inputs: Map<String, WorkflowInputDef>,
): Map<String, InputYaml>? = inputs.takeIf { it.isNotEmpty() }?.mapValues { (_, def) ->
    InputYaml(
        description = def.description,
        type = def.type.yamlName(),
        required = def.required,
        default = def.default?.toYamlDefault(),
    )
}

private fun InputDefault.toYamlDefault(): YamlDefault = when (this) {
    is InputDefault.StringDefault  -> YamlDefault.StringValue(value)
    is InputDefault.BooleanDefault -> YamlDefault.BooleanValue(value)
}
