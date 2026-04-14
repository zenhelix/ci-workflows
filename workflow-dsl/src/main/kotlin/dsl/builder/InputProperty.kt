package dsl.builder

import dsl.core.InputRef
import dsl.core.WorkflowInput
import kotlin.reflect.KProperty

class InputProperty<T>(
    private val input: WorkflowInput,
    private val get: (String) -> T,
    private val set: (T) -> String,
) {
    operator fun getValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>): T =
        get(builder.getInput(input))

    operator fun setValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>, value: T) {
        builder.setInput(input, set(value))
    }
}

fun stringInput(input: WorkflowInput) = InputProperty<String>(input, { it }, { it })
fun refInput(input: WorkflowInput) = InputProperty<InputRef>(input, ::InputRef, InputRef::expression)
