package ai.openonion.oochat.domain.model

import java.util.Locale

/** A tool the connected Host exposes to the direct-execution form. */
data class AgentTool(
    val name: String,
    val description: String? = null,
    val parameters: List<AgentToolParameter> = emptyList(),
    /** User-facing name supplied by the Host when it has one. */
    val displayName: String? = null,
    /** Text for the primary action button, for example "Search records". */
    val actionLabel: String? = null,
    /** A second confirmation is required before this action is sent. */
    val requiresConfirmation: Boolean = false,
    /** True when the Host sent structured metadata, even for a no-argument tool. */
    val metadataAvailable: Boolean = false,
    /** False when the Host omitted or malformed one of the declared inputs. */
    val metadataComplete: Boolean = true
) {
    /** Whether this tool can be presented safely to a normal user. */
    val isFormAvailable: Boolean
        get() {
            if (!metadataComplete || (!metadataAvailable && parameters.isEmpty())) return false
            val names = parameters.map { it.name.trim() }
            return names.all(String::isNotBlank) &&
                names.size == names.toSet().size &&
                parameters.all(AgentToolParameter::isFormSupported)
        }
}

/** One input accepted by an [AgentTool]. */
data class AgentToolParameter(
    val name: String,
    val type: String? = null,
    val description: String? = null,
    val required: Boolean = false,
    val choices: List<String> = emptyList(),
    /** User-facing field label; the parameter name remains the wire key. */
    val label: String? = null,
    /** Text shown inside an empty text field. */
    val placeholder: String? = null,
    /** A short example shown when no placeholder was supplied. */
    val example: String? = null,
    /** Initial value supplied by the Host, kept as text until validation. */
    val defaultValue: String? = null
)

private fun AgentToolParameter.isFormSupported(): Boolean {
    val normalizedType = type?.trim()?.lowercase(Locale.ROOT)
    return when {
        choices.isNotEmpty() -> true
        normalizedType in FORM_TEXT_TYPES -> true
        normalizedType in FORM_INTEGER_TYPES -> true
        normalizedType in FORM_NUMBER_TYPES -> true
        normalizedType in FORM_BOOLEAN_TYPES -> true
        else -> false
    }
}

private val FORM_TEXT_TYPES = setOf("string", "text", "textarea")
private val FORM_INTEGER_TYPES = setOf("integer", "int", "long")
private val FORM_NUMBER_TYPES = setOf("number", "decimal", "float", "double")
private val FORM_BOOLEAN_TYPES = setOf("boolean", "bool", "yes/no", "yes_no", "yes-no")
