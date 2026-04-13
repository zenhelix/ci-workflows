package dsl.yaml

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// ---- kaml config ------------------------------------------------------------

val adapterWorkflowYaml: Yaml = Yaml(
    configuration = YamlConfiguration(
        encodeDefaults = false,
        singleLineStringStyle = SingleLineStringStyle.SingleQuoted,
    )
)

// ---- Top-level structure ----------------------------------------------------

@Serializable
data class AdapterWorkflowYaml(
    val name: String,
    val on: TriggerYaml,
    val jobs: Map<String, JobYaml>,
)

@Serializable
data class TriggerYaml(
    @SerialName("workflow_call")
    val workflowCall: WorkflowCallBodyYaml,
)

@Serializable
data class WorkflowCallBodyYaml(
    val inputs: Map<String, InputYaml>? = null,
    val secrets: Map<String, SecretYaml>? = null,
)

// ---- Input / Secret ---------------------------------------------------------

@Serializable
data class InputYaml(
    val description: String,
    val type: String,
    val required: Boolean,
    val default: YamlDefault? = null,
)

@Serializable
data class SecretYaml(
    val description: String,
    val required: Boolean,
)

// ---- Job --------------------------------------------------------------------

@Serializable
data class JobYaml(
    val needs: NeedsYaml? = null,
    val strategy: StrategyYaml? = null,
    val uses: String,
    val with: Map<String, String>? = null,
    val secrets: Map<String, String>? = null,
)

@Serializable
data class StrategyYaml(
    val matrix: Map<String, String>,
)

// ---- needs: scalar-or-list --------------------------------------------------

/**
 * When there is exactly one dependency GitHub Actions accepts a scalar string;
 * when there are multiple it must be a list.  We model this with a sealed
 * class so the serializer can emit the right YAML form.
 */
@Serializable(with = NeedsYamlSerializer::class)
sealed class NeedsYaml {
    data class Single(val value: String) : NeedsYaml()
    data class Multiple(val values: List<String>) : NeedsYaml()

    companion object {
        fun of(list: List<String>): NeedsYaml? = when (list.size) {
            0 -> null
            1 -> Single(list.first())
            else -> Multiple(list)
        }
    }
}

object NeedsYamlSerializer : KSerializer<NeedsYaml> {
    private val listSerializer = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NeedsYaml", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NeedsYaml) {
        when (value) {
            is NeedsYaml.Single   -> encoder.encodeString(value.value)
            is NeedsYaml.Multiple -> listSerializer.serialize(encoder, value.values)
        }
    }

    override fun deserialize(decoder: Decoder): NeedsYaml =
        NeedsYaml.Single(decoder.decodeString())
}

// ---- default: string-or-boolean ---------------------------------------------

/**
 * Input defaults can be a string ('17') or a boolean (true / false).
 * Booleans must NOT be single-quoted in YAML; strings must be.
 */
@Serializable(with = YamlDefaultSerializer::class)
sealed class YamlDefault {
    data class StringValue(val value: String) : YamlDefault()
    data class BooleanValue(val value: Boolean) : YamlDefault()
}

object YamlDefaultSerializer : KSerializer<YamlDefault> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("YamlDefault", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: YamlDefault) {
        when (value) {
            is YamlDefault.StringValue  -> encoder.encodeString(value.value)
            is YamlDefault.BooleanValue -> encoder.encodeBoolean(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): YamlDefault =
        YamlDefault.StringValue(decoder.decodeString())
}
