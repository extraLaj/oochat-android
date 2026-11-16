package ai.openonion.oochat.data.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.Locale

/** Reserved for LLM tool calls; direct EXEC never sends it. */
internal const val LLM_TOOL_SUMMARY_PARAMETER = "summary"

/**
 * One entry of an `AGENT_PROFILE` frame's `tools` list.
 *
 * Older Hosts send a bare tool-name string. The custom serializer accepts that
 * form as well as the Host function schema used by the direct-tool form:
 * `{"name": …, "description": …, "parameters": {"type":"object", …}}`.
 */
@Serializable(with = ToolWireSerializer::class)
data class ToolWire(
    val name: String,
    val description: String? = null,
    val parameters: List<ToolParameterWire> = emptyList(),
    val displayName: String? = null,
    val actionLabel: String? = null,
    val requiresConfirmation: Boolean = false,
    /** Distinguishes a structured no-argument tool from a legacy name string. */
    val metadataAvailable: Boolean = false,
    /** False when the structured entry declared an input that could not be read safely. */
    val metadataComplete: Boolean = true
)

/** Parameter metadata read from a structured [ToolWire]'s JSON Schema. */
data class ToolParameterWire(
    val name: String,
    val type: String? = null,
    val description: String? = null,
    val required: Boolean = false,
    val choices: List<String> = emptyList(),
    val label: String? = null,
    val placeholder: String? = null,
    val example: String? = null,
    val defaultValue: String? = null
)

/**
 * Decoder for the established tool metadata contract.
 *
 * The profile is connection-critical: rejecting the whole frame because one
 * tool has an unexpected shape would also hide the agent name, model and
 * every other tool. An unexpected structured entry therefore stays available
 * to the developer JSON path, but never guesses a normal-user form.
 */
object ToolWireSerializer : KSerializer<ToolWire> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ai.openonion.oochat.data.protocol.ToolWire") {
            element<String>("name")
            element<String?>("description", isOptional = true)
            element<JsonElement>("parameters", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): ToolWire {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("ToolWire can only be read from JSON")
        return fromJson(input.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: ToolWire) {
        val output = encoder as? JsonEncoder
            ?: throw SerializationException("ToolWire can only be written as JSON")
        output.encodeJsonElement(
            buildJsonObject {
                put("name", value.name)
                value.description?.let { put("description", it) }
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        value.parameters.forEach { parameter ->
                            put(parameter.name, parameter.toJsonSchema())
                        }
                    })
                    val required = value.parameters.filter { it.required }.map { it.name }
                    if (required.isNotEmpty()) {
                        putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
                    }
                })
            }
        )
    }

    private fun fromJson(element: JsonElement): ToolWire = when (element) {
        is JsonPrimitive -> ToolWire(
            name = element.takeIf { it.isString }?.contentOrNull.orEmpty()
        )
        is JsonObject -> {
            val parameterResult = readParameters(element["parameters"])
            ToolWire(
                name = element.stringValue("name").orEmpty(),
                description = element.stringValue("description"),
                parameters = parameterResult.parameters,
                metadataAvailable = true,
                metadataComplete = parameterResult.complete
            )
        }
        else -> ToolWire(name = "")
    }

    private fun readParameters(element: JsonElement?): ParameterReadResult {
        val schema = element as? JsonObject
            ?: return ParameterReadResult(emptyList(), complete = false)
        if (schema.stringValue("type") != "object") {
            return ParameterReadResult(emptyList(), complete = false)
        }
        val properties = schema["properties"] as? JsonObject
            ?: return ParameterReadResult(emptyList(), complete = false)
        val requiredNames = schema.stringArray("required")
            ?: return ParameterReadResult(emptyList(), complete = false)

        val executableProperties = properties.filterKeys { it != LLM_TOOL_SUMMARY_PARAMETER }
        val executableRequiredNames = requiredNames.filterNot { it == LLM_TOOL_SUMMARY_PARAMETER }
        val executableRequiredNameSet = executableRequiredNames.toSet()
        var complete = executableRequiredNames.size == executableRequiredNameSet.size &&
            executableRequiredNames.all { it in executableProperties }
        val parameters = executableProperties.entries.mapNotNull { (name, propertySchema) ->
            val parameter = (propertySchema as? JsonObject)?.let {
                readParameter(name, it, executableRequiredNameSet)
            }
            if (parameter == null || !parameter.isCompleteForForm()) complete = false
            parameter
        }
        return parameterResult(parameters, complete)
    }

    private data class ParameterReadResult(
        val parameters: List<ToolParameterWire>,
        val complete: Boolean
    )

    private fun parameterResult(
        parameters: List<ToolParameterWire>,
        complete: Boolean
    ): ParameterReadResult {
        val names = parameters.map { it.name.trim() }
        return ParameterReadResult(
            parameters = parameters,
            complete = complete && names.size == names.toSet().size
        )
    }

    private fun ToolParameterWire.isCompleteForForm(): Boolean {
        val normalizedType = type?.trim()?.lowercase(Locale.ROOT)
        return name.isNotBlank() && name == name.trim() &&
            normalizedType in FORM_TYPES &&
            (choices.isEmpty() || normalizedType == "string")
    }

    private fun readParameter(
        name: String,
        schema: JsonObject,
        requiredNames: Set<String>
    ): ToolParameterWire? {
        val choices = schema.stringArray("enum", allowEmpty = false) ?: return null
        if (choices.size != choices.toSet().size) return null
        return ToolParameterWire(
            name = name,
            type = schema.stringValue("type"),
            description = schema.stringValue("description"),
            required = name in requiredNames,
            choices = choices,
            defaultValue = schema.primitiveValue("default")
        )
    }

    private fun ToolParameterWire.toJsonSchema(): JsonObject = buildJsonObject {
        type?.let { put("type", it) }
        description?.let { put("description", it) }
        defaultValue?.let { put("default", it) }
        if (choices.isNotEmpty()) {
            putJsonArray("enum") { choices.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun JsonObject.primitiveValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.stringArray(key: String, allowEmpty: Boolean = true): List<String>? {
        val values = this[key] ?: return emptyList()
        val array = values as? JsonArray ?: return null
        if (array.isEmpty() && !allowEmpty) return null
        val strings = array.map { item ->
            val value = (item as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.contentOrNull
                ?: return null
            if (value.isBlank() || value != value.trim()) return null
            value
        }
        return strings
    }

    private val FORM_TYPES = setOf("string", "integer", "number", "boolean")
}
