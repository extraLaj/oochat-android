package ai.openonion.oochat.ui.chat

import ai.openonion.oochat.domain.model.AgentTool
import ai.openonion.oochat.domain.model.AgentToolParameter
import ai.openonion.oochat.domain.model.ToolArgumentBuildResult
import ai.openonion.oochat.domain.model.ToolArgumentBuilder
import ai.openonion.oochat.ui.theme.Elevation
import ai.openonion.oochat.ui.theme.hairline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/** A form for sending a direct EXEC command. */
internal const val EXEC_TOOL_SELECTOR_TAG = "exec_tool_selector"
internal const val EXEC_TOOL_SUBMIT_TAG = "exec_tool_submit"

@Composable
internal fun ExecToolPanel(
    tools: List<AgentTool>,
    onExecute: (tool: String, argsJson: String) -> Unit,
    allowRawJson: Boolean = true,
    modifier: Modifier = Modifier
) {
    val usableTools = tools.filter { it.name.isNotBlank() && (allowRawJson || it.isFormAvailable) }
    if (usableTools.isEmpty()) return

    val toolKeys = usableTools.map { it.name }
    var expanded by rememberSaveable(toolKeys, allowRawJson) {
        mutableStateOf(false)
    }
    var choosingAction by rememberSaveable(toolKeys, allowRawJson) {
        mutableStateOf(!allowRawJson && usableTools.size > 1)
    }
    var toolMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedToolName by rememberSaveable(toolKeys) {
        mutableStateOf(toolKeys.firstOrNull().orEmpty())
    }
    val selectedTool = usableTools.firstOrNull { it.name == selectedToolName } ?: usableTools.firstOrNull()
    val selectedToolKey = selectedTool
    val hasStructuredInputs = selectedTool?.parameters?.isNotEmpty() == true
    val selectedToolHasForm = selectedTool?.isFormAvailable == true
    var jsonMode by rememberSaveable(selectedToolKey) {
        mutableStateOf(selectedTool?.let { !it.isFormAvailable || it.parameters.isEmpty() } ?: true)
    }
    var argsJson by rememberSaveable(selectedToolKey) { mutableStateOf("{}") }
    val initialValues = remember(selectedToolKey) {
        selectedTool?.parameters.orEmpty()
            .mapNotNull { parameter ->
                val value = parameter.defaultValue
                    ?: "false".takeIf { parameter.required && isBooleanParameter(parameter) }
                value?.let { parameter.name to it }
            }
            .toMap()
    }
    var inputValues by rememberSaveable(selectedToolKey) { mutableStateOf(initialValues) }
    var touchedFields by rememberSaveable(selectedToolKey) { mutableStateOf<Set<String>>(emptySet()) }
    var submitted by rememberSaveable(selectedToolKey) { mutableStateOf(false) }
    var optionalFieldsExpanded by rememberSaveable(selectedToolKey) { mutableStateOf(false) }
    var confirming by rememberSaveable(selectedToolKey) { mutableStateOf(false) }

    val argsError = remember(argsJson) { validateExecArguments(argsJson) }
    val typedResult = remember(selectedToolKey, inputValues) {
        selectedTool?.let { ToolArgumentBuilder.build(it, inputValues) }
    }
    val fieldErrors = (typedResult as? ToolArgumentBuildResult.Invalid)
        ?.errors
        ?.associateBy { it.parameterName }
        .orEmpty()
    val showingJson = allowRawJson && (jsonMode || !hasStructuredInputs)
    val requiredParameters = selectedTool?.parameters.orEmpty().filter { it.required }
    val optionalParameters = selectedTool?.parameters.orEmpty().filterNot { it.required }

    fun markFieldChanged(parameter: AgentToolParameter, value: String) {
        inputValues = inputValues + (parameter.name to value)
        touchedFields = touchedFields + parameter.name
        confirming = false
    }

    fun submitStructuredForm() {
        when (val result = typedResult) {
            is ToolArgumentBuildResult.Success -> {
                if (selectedTool?.requiresConfirmation == true && !confirming) {
                    confirming = true
                } else {
                    selectedTool?.let { onExecute(it.name, result.arguments.toString()) }
                    confirming = false
                }
            }

            is ToolArgumentBuildResult.Invalid -> {
                submitted = true
                confirming = false
                if (result.errors.any { error ->
                        optionalParameters.any { it.name == error.parameterName }
                    }
                ) {
                    optionalFieldsExpanded = true
                }
            }

            null -> Unit
        }
    }

    fun submitAction() {
        if (showingJson) {
            val tool = selectedTool ?: return
            if (argsError != null) return
            if (tool.requiresConfirmation && !confirming) {
                confirming = true
            } else {
                onExecute(tool.name, argsJson)
                confirming = false
            }
        } else {
            submitStructuredForm()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (allowRawJson) "Direct tool execution" else "Actions",
                modifier = Modifier.weight(1f)
            )

            TextButton(onClick = {
                expanded = !expanded
                confirming = false
            }) {
                Text(if (expanded) "Hide" else "Show")
            }
        }

        if (expanded && !allowRawJson && choosingAction) {
            Text(
                text = "Choose an action",
                style = MaterialTheme.typography.titleSmall
            )
            usableTools.forEach { tool ->
                ToolActionCard(
                    tool = tool,
                    onClick = {
                        selectedToolName = tool.name
                        choosingAction = false
                    }
                )
            }
        }

        if (expanded && (allowRawJson || !choosingAction)) {
            if (allowRawJson) {
                ToolSelector(
                    tools = usableTools,
                    selectedTool = selectedTool,
                    expanded = toolMenuExpanded,
                    onExpand = { toolMenuExpanded = true },
                    onDismiss = { toolMenuExpanded = false },
                    onSelect = { tool ->
                        selectedToolName = tool.name
                        toolMenuExpanded = false
                    }
                )
            } else if (usableTools.size > 1) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedTool?.toUserFacingName() ?: "Choose an action",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        choosingAction = true
                        confirming = false
                    }) {
                        Text("Change action")
                    }
                }
            } else {
                Text(
                    text = selectedTool?.toUserFacingName() ?: "Choose an action",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            selectedTool?.description
                ?.takeIf(String::isNotBlank)
                ?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

            if (allowRawJson && hasStructuredInputs && selectedToolHasForm) {
                TextButton(onClick = {
                    jsonMode = !jsonMode
                    confirming = false
                }) {
                    Text(if (jsonMode) "Use fields" else "Use JSON")
                }
            }

            if (showingJson) {
                OutlinedTextField(
                    value = argsJson,
                    onValueChange = {
                        argsJson = it
                        confirming = false
                    },
                    label = { Text("Arguments JSON") },
                    supportingText = {
                        Text(argsError ?: "Enter a JSON object, for example: {\"command\":\"pwd\"}")
                    },
                    isError = argsError != null,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (selectedToolHasForm && hasStructuredInputs) {
                requiredParameters.forEach { parameter ->
                    ToolParameterInput(
                        parameter = parameter,
                        value = inputValues[parameter.name].orEmpty(),
                        error = fieldErrors[parameter.name]
                            ?.message
                            ?.takeIf { submitted || parameter.name in touchedFields },
                        onValueChange = { value -> markFieldChanged(parameter, value) }
                    )
                }

                if (optionalParameters.isNotEmpty()) {
                    TextButton(onClick = { optionalFieldsExpanded = !optionalFieldsExpanded }) {
                        Text(if (optionalFieldsExpanded) "Fewer options" else "More options")
                    }
                    if (optionalFieldsExpanded) {
                        optionalParameters.forEach { parameter ->
                            ToolParameterInput(
                                parameter = parameter,
                                value = inputValues[parameter.name].orEmpty(),
                                error = fieldErrors[parameter.name]
                                    ?.message
                                    ?.takeIf { submitted || parameter.name in touchedFields },
                                onValueChange = { value -> markFieldChanged(parameter, value) }
                            )
                        }
                    }
                }

                fieldErrors[null]?.message?.takeIf { submitted }?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (selectedToolHasForm) {
                Text("Nothing else is needed for this action.")
            }

            if (confirming && selectedTool?.requiresConfirmation == true) {
                Text(
                    text = "This action may change data. Do you want to continue?",
                    color = MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { confirming = false }) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { submitAction() },
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Confirm ${selectedTool.actionButtonLabel()}")
                    }
                }
            } else {
                Button(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag(EXEC_TOOL_SUBMIT_TAG),
                    enabled = selectedTool != null && when {
                        showingJson -> argsError == null
                        selectedToolHasForm -> true
                        else -> false
                    },
                    onClick = { submitAction() }
                ) {
                    Text(selectedTool?.actionButtonLabel() ?: "Continue")
                }
            }
        }
    }
}

@Composable
private fun ToolActionCard(
    tool: AgentTool,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(tool.toUserFacingName())
            tool.description
                ?.takeIf(String::isNotBlank)
                ?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ToolSelector(
    tools: List<AgentTool>,
    selectedTool: AgentTool?,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (AgentTool) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onExpand,
            shape = MaterialTheme.shapes.large,
            enabled = tools.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EXEC_TOOL_SELECTOR_TAG)
        ) {
            Text(selectedTool?.toUserFacingName() ?: "Choose an action")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            shadowElevation = Elevation.hairline,
            border = MaterialTheme.hairline
        ) {
            tools.forEach { tool ->
                DropdownMenuItem(
                    modifier = Modifier.testTag("$EXEC_TOOL_SELECTOR_TAG:${tool.name}"),
                    text = { Text(tool.toUserFacingName()) },
                    onClick = { onSelect(tool) }
                )
            }
        }
    }
}

@Composable
private fun ToolParameterInput(
    parameter: AgentToolParameter,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit
) {
    val label = parameter.toUserFacingLabel()
    when {
        isBooleanParameter(parameter) -> {
            ToolBooleanInput(
                parameter = parameter,
                label = label,
                value = value,
                error = error,
                onValueChange = onValueChange
            )
        }

        parameter.choices.isNotEmpty() -> {
            ToolChoiceInput(
                label = label,
                value = value,
                options = parameter.choices,
                description = parameter.description,
                error = error,
                onValueChange = onValueChange
            )
        }

        else -> {
            val supportingText = error ?: parameter.description ?: parameter.example?.let { "Example: $it" }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = parameter.placeholder?.let { hint -> { Text(hint) } }
                    ?: parameter.example?.let { example -> { Text(example) } },
                supportingText = supportingText?.let { text ->
                    {
                        Text(
                            text = text,
                            color = if (error != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                isError = error != null,
                minLines = if (isTextArea(parameter)) 3 else 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = inputKeyboardType(parameter)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ToolBooleanInput(
    parameter: AgentToolParameter,
    label: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            val supportingText = error ?: parameter.description
            supportingText?.let { text ->
                Text(
                    text = text,
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Switch(
            checked = value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true),
            onCheckedChange = { checked -> onValueChange(if (checked) "true" else "false") },
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

@Composable
private fun ToolChoiceInput(
    label: String,
    value: String,
    options: List<String>,
    description: String?,
    error: String?,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(value.takeIf(String::isNotBlank)?.toChoiceLabel() ?: "Choose $label")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shadowElevation = Elevation.hairline,
            border = MaterialTheme.hairline
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toChoiceLabel()) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }

    val supportingText = error ?: description
    supportingText?.let { text ->
        Text(
            text = text,
            color = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}

internal fun AgentTool.toUserFacingName(): String =
    displayName?.takeIf(String::isNotBlank) ?: name.toReadableToolLabel()

internal fun AgentTool.actionButtonLabel(): String =
    actionLabel?.takeIf(String::isNotBlank) ?: toUserFacingName()

private fun AgentToolParameter.toUserFacingLabel(): String {
    val readable = label?.takeIf(String::isNotBlank) ?: name.toReadableToolLabel()
    return if (required) "$readable (required)" else readable
}

private fun isBooleanParameter(parameter: AgentToolParameter): Boolean =
    parameter.type?.trim()?.lowercase(Locale.ROOT) in BOOLEAN_TYPES

private fun isTextArea(parameter: AgentToolParameter): Boolean =
    parameter.type?.trim()?.lowercase(Locale.ROOT) == "textarea"

private fun inputKeyboardType(parameter: AgentToolParameter): KeyboardType {
    val type = parameter.type?.trim()?.lowercase(Locale.ROOT)
    return when {
        type in INTEGER_TYPES -> KeyboardType.Number
        type in NUMBER_TYPES -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }
}

internal fun String.toReadableToolLabel(): String =
    toReadableLabel(replaceHyphens = true)

private fun String.toReadableLabel(replaceHyphens: Boolean): String =
    replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace('_', ' ')
        .let { value -> if (replaceHyphens) value.replace('-', ' ') else value }
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" ") { word ->
            word.replaceFirstChar { character -> character.uppercase() }
        }

private fun String.toChoiceLabel(): String = when (lowercase(Locale.ROOT)) {
    "yes" -> "Yes"
    "no" -> "No"
    else -> toReadableLabel(replaceHyphens = false)
}

private val INTEGER_TYPES = setOf("integer", "int", "long")
private val NUMBER_TYPES = setOf("number", "decimal", "float", "double")
private val BOOLEAN_TYPES = setOf("boolean", "bool", "yes/no", "yes_no", "yes-no")

private fun validateExecArguments(argsJson: String): String? {
    if (argsJson.isBlank()) return "Arguments are required"

    val element = runCatching { Json.parseToJsonElement(argsJson) }.getOrNull()
        ?: return "Arguments must be valid JSON"
    return if (element is JsonObject) {
        null
    } else {
        "Arguments must be a JSON object"
    }
}
