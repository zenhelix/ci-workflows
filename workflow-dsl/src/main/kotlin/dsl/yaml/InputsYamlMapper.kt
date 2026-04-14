package dsl.yaml

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

fun toInputsYaml(
    inputs: Map<String, WorkflowCall.Input>,
    booleanDefaults: Map<String, Boolean>,
): Map<String, InputYaml>? =
    inputs.takeIf { it.isNotEmpty() }?.mapValues { (name, input) ->
        val default = when {
            name in booleanDefaults -> YamlDefault.BooleanValue(booleanDefaults.getValue(name))
            input.default != null   -> YamlDefault.StringValue(input.default!!)
            else                    -> null
        }
        InputYaml(
            description = input.description,
            type = input.type.name.lowercase(),
            required = input.required,
            default = default,
        )
    }
