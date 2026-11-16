package ai.openonion.oochat.domain.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.util.Locale

/** A tool argument validation error. */
data class ToolArgumentError(
    val parameterName: String?,
    val message: String
)

/** The result of building a tool's JSON arguments. */
sealed interface ToolArgumentBuildResult {
    data class Success(val arguments: JsonObject) : ToolArgumentBuildResult

    data class Invalid(val errors: List<ToolArgumentError>) : ToolArgumentBuildResult
}

/** Converts form values into typed JSON for an EXEC request. */
object ToolArgumentBuilder {

    private val textTypes = setOf("string", "text", "textarea")
    private val integerTypes = setOf("integer", "int", "long")
    private val numberTypes = setOf("number", "decimal", "float", "double")
    private val booleanTypes = setOf("boolean", "bool", "yes/no", "yes_no", "yes-no")
    private val choiceTypes = setOf("choice", "enum", "select")

    /** Builds arguments and returns all field errors. */
    fun build(
        tool: AgentTool,
        values: Map<String, String?>
    ): ToolArgumentBuildResult {
        val errors = mutableListOf<ToolArgumentError>()

        if (tool.name.isBlank()) {
            errors += ToolArgumentError(null, "This tool does not have a name")
        }

        val unnamedParameters = tool.parameters.count { it.name.isBlank() }
        if (unnamedParameters > 0) {
            errors += ToolArgumentError(null, "This tool has an unnamed input")
        }

        val duplicateNames = tool.parameters
            .filter { it.name.isNotBlank() }
            .groupingBy { it.name.trim() }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        duplicateNames.forEach { name ->
            errors += ToolArgumentError(name, "This tool defines this input more than once")
        }

        if (errors.isNotEmpty()) return ToolArgumentBuildResult.Invalid(errors)

        val arguments = linkedMapOf<String, JsonPrimitive>()
        tool.parameters.forEach { parameter ->
            val raw = values[parameter.name]
            if (raw.isNullOrBlank()) {
                if (parameter.required) {
                    errors += ToolArgumentError(parameter.name, "This field is required")
                }
                return@forEach
            }

            val type = parameter.type
                ?.trim()
                ?.lowercase(Locale.ROOT)

            val converted = when {
                type in booleanTypes -> parseBoolean(raw)
                type in integerTypes -> parseNumber(raw, integer = true)
                type in numberTypes -> parseNumber(raw, integer = false)
                type in choiceTypes || parameter.choices.isNotEmpty() -> parseChoice(parameter, raw)
                type in textTypes -> JsonPrimitive(raw)
                else -> null
            }

            if (converted == null) {
                errors += ToolArgumentError(parameter.name, errorMessage(parameter, type))
            } else {
                arguments[parameter.name] = converted
            }
        }

        return if (errors.isEmpty()) {
            ToolArgumentBuildResult.Success(JsonObject(arguments))
        } else {
            ToolArgumentBuildResult.Invalid(errors)
        }
    }

    private fun parseBoolean(raw: String): JsonPrimitive? = when (
        raw.trim().lowercase(Locale.ROOT)
    ) {
        "true", "yes" -> JsonPrimitive(true)
        "false", "no" -> JsonPrimitive(false)
        else -> null
    }

    private fun parseNumber(raw: String, integer: Boolean): JsonPrimitive? {
        val exact = runCatching { BigDecimal(raw.trim()) }.getOrNull() ?: return null
        return if (integer) {
            runCatching { JsonPrimitive(exact.toBigIntegerExact()) }.getOrNull()
        } else {
            JsonPrimitive(exact)
        }
    }

    private fun parseChoice(parameter: AgentToolParameter, raw: String): JsonPrimitive? {
        val selected = raw.trim()
        return parameter.choices
            .firstOrNull { it == selected }
            ?.let(::JsonPrimitive)
    }

    private fun errorMessage(parameter: AgentToolParameter, type: String?): String = when {
        type in booleanTypes -> "Choose yes or no"
        type in integerTypes -> "Enter a whole number"
        type in numberTypes -> "Enter a valid number"
        type in choiceTypes || parameter.choices.isNotEmpty() -> "Choose one of the available options"
        type in textTypes -> "Enter a value"
        type.isNullOrBlank() -> "This input does not have a supported type"
        else -> "This input type is not supported"
    }
}
