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

val adapterWorkflowYaml: Yaml = Yaml(
    configuration = YamlConfiguration(
        encodeDefaults = false,
        singleLineStringStyle = SingleLineStringStyle.SingleQuoted,
        breakScalarsAt = Int.MAX_VALUE,
    )
)

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

@JvmInline
@Serializable(with = NeedsYamlSerializer::class)
value class NeedsYaml(val values: List<String>) {
    companion object {
        fun of(list: List<String>): NeedsYaml? =
            if (list.isEmpty()) null else NeedsYaml(list)
    }
}

object NeedsYamlSerializer : KSerializer<NeedsYaml> {
    private val listSerializer = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NeedsYaml", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NeedsYaml) {
        if (value.values.size == 1) encoder.encodeString(value.values.first())
        else listSerializer.serialize(encoder, value.values)
    }

    override fun deserialize(decoder: Decoder): NeedsYaml =
        NeedsYaml(listOf(decoder.decodeString()))
}

@Serializable(with = YamlDefaultSerializer::class)
sealed interface YamlDefault {
    data class StringValue(val value: String) : YamlDefault
    data class BooleanValue(val value: Boolean) : YamlDefault
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
