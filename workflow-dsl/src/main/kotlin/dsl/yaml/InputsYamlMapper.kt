package dsl.yaml

import dsl.core.InputDefault
import dsl.core.WorkflowInputDef

fun toInputsYaml(
    inputs: Map<String, WorkflowInputDef>,
): Map<String, InputYaml>? =
    inputs.takeIf { it.isNotEmpty() }?.mapValues { (_, def) ->
        val default = when (val d = def.default) {
            is InputDefault.StringDefault  -> YamlDefault.StringValue(d.value)
            is InputDefault.BooleanDefault -> YamlDefault.BooleanValue(d.value)
            null                           -> null
        }
        InputYaml(
            description = def.description,
            type = def.type.yamlName(),
            required = def.required,
            default = default,
        )
    }
