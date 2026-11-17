package ai.openonion.oochat.domain.model

/**
 * Forms the app ships for ConnectOnion's standard tools.
 *
 * ConnectOnion's Host sends `AGENT_PROFILE.tools` as bare names
 * (`sample.tools.names()`), so on its own a tool never gets a normal-user form
 * (W16AAPPLE-116). Until the Host sends a direct-EXEC schema, the app fills in
 * the form for a small, read-mostly set of the standard co-ai tools.
 *
 * The parameter names are copied from ConnectOnion 1.8.8's own
 * `to_function_schema()` for each tool, minus the LLM-only `summary`, so
 * EXEC args match what `tool(**args)` accepts. Labels and descriptions are the
 * app's own plain-language wording.
 *
 * Safety rules:
 * - A Host schema always wins: only a bare-name tool is filled in.
 * - A form applies only when the Host lists its whole tool group. Names such as
 *   `list` or `clear` are generic; seeing the full ConnectOnion group is what
 *   makes it likely they are ConnectOnion's tools and not another agent's.
 * - Tools that run commands or change files (`bash`, `write`, `edit`, …) are
 *   never given a form; they stay developer-only.
 */
object BuiltInToolForms {

    private data class ToolGroup(
        /** Every name the Host must list before any form in [forms] applies. */
        val fingerprint: Set<String>,
        val forms: List<AgentTool>
    )

    private val fileTools = ToolGroup(
        fingerprint = setOf("read_file", "glob", "grep", "edit", "write", "multi_edit"),
        forms = listOf(
            AgentTool(
                name = "read_file",
                displayName = "Read a file",
                actionLabel = "Read file",
                description = "Show the contents of a file in the agent's workspace.",
                parameters = listOf(
                    AgentToolParameter(
                        name = "path",
                        type = "string",
                        required = true,
                        label = "File path",
                        placeholder = "README.md",
                        description = "Where the file is, starting from the workspace folder."
                    ),
                    AgentToolParameter(
                        name = "offset",
                        type = "integer",
                        label = "Start at line",
                        description = "Line number to start reading from. The first line is 1."
                    ),
                    AgentToolParameter(
                        name = "limit",
                        type = "integer",
                        label = "Number of lines",
                        description = "How many lines to show."
                    )
                )
            ),
            AgentTool(
                name = "glob",
                displayName = "Find files",
                actionLabel = "Find files",
                description = "List files whose names match a pattern.",
                parameters = listOf(
                    AgentToolParameter(
                        name = "pattern",
                        type = "string",
                        required = true,
                        label = "File name pattern",
                        placeholder = "**/*.pdf",
                        description = "Use * for any name, for example *.pdf or reports/**/*.xlsx."
                    ),
                    AgentToolParameter(
                        name = "path",
                        type = "string",
                        label = "Folder",
                        description = "Folder to look in. Leave empty for the whole workspace."
                    )
                )
            ),
            AgentTool(
                name = "grep",
                displayName = "Search inside files",
                actionLabel = "Search",
                description = "Find files that contain some text.",
                parameters = listOf(
                    AgentToolParameter(
                        name = "pattern",
                        type = "string",
                        required = true,
                        label = "Text to find",
                        placeholder = "invoice",
                        description = "The words to look for."
                    ),
                    AgentToolParameter(
                        name = "path",
                        type = "string",
                        label = "Folder or file",
                        description = "Where to search. Leave empty for the whole workspace."
                    ),
                    AgentToolParameter(
                        name = "file_pattern",
                        type = "string",
                        label = "Only these files",
                        placeholder = "*.md",
                        description = "Limit the search to file names like this."
                    ),
                    AgentToolParameter(
                        name = "output_mode",
                        type = "string",
                        choices = listOf("files", "content", "count"),
                        label = "Show",
                        description = "Matching file names, the matching lines, or a count per file."
                    ),
                    AgentToolParameter(
                        name = "ignore_case",
                        type = "boolean",
                        label = "Ignore capital letters",
                        description = "Match \"Invoice\" and \"invoice\" alike."
                    ),
                    AgentToolParameter(
                        name = "max_results",
                        type = "integer",
                        label = "Maximum results",
                        description = "Stop after this many matches."
                    )
                )
            )
        )
    )

    private val todoTools = ToolGroup(
        fingerprint = setOf("add", "clear", "complete", "list", "remove", "start", "update"),
        forms = listOf(
            AgentTool(
                name = "list",
                displayName = "Show to-do list",
                actionLabel = "Show list",
                description = "See the agent's current to-do items.",
                parameters = emptyList()
            ),
            AgentTool(
                name = "add",
                displayName = "Add a to-do",
                actionLabel = "Add to-do",
                description = "Add an item to the agent's to-do list.",
                parameters = listOf(
                    AgentToolParameter(
                        name = "content",
                        type = "string",
                        required = true,
                        label = "To-do",
                        placeholder = "Send the quarterly report",
                        description = "What needs to be done."
                    ),
                    AgentToolParameter(
                        name = "active_form",
                        type = "string",
                        required = true,
                        label = "While in progress, show",
                        placeholder = "Sending the quarterly report",
                        description = "How the item reads while it is being worked on."
                    ),
                    AgentToolParameter(
                        name = "priority",
                        type = "string",
                        choices = listOf("high", "medium", "low"),
                        label = "Priority",
                        description = "Medium if you leave it empty."
                    )
                )
            ),
            AgentTool(
                name = "start",
                displayName = "Start a to-do",
                actionLabel = "Mark as started",
                description = "Mark a to-do as in progress.",
                parameters = listOf(todoContentParameter())
            ),
            AgentTool(
                name = "complete",
                displayName = "Finish a to-do",
                actionLabel = "Mark as done",
                description = "Mark a to-do as done.",
                parameters = listOf(todoContentParameter())
            ),
            AgentTool(
                name = "remove",
                displayName = "Remove a to-do",
                actionLabel = "Remove to-do",
                description = "Delete one item from the to-do list.",
                requiresConfirmation = true,
                parameters = listOf(todoContentParameter())
            ),
            AgentTool(
                name = "clear",
                displayName = "Clear to-do list",
                actionLabel = "Clear list",
                description = "Delete every item on the to-do list.",
                requiresConfirmation = true,
                parameters = emptyList()
            )
        )
    )

    private val groups = listOf(fileTools, todoTools)

    /**
     * Returns [tools] with built-in forms filled in for bare-name tools whose
     * whole group the Host listed. Order and every other tool are unchanged.
     */
    fun apply(tools: List<AgentTool>): List<AgentTool> {
        val bareNames = tools.filterNot { it.metadataAvailable }.map { it.name }.toSet()
        val allNames = tools.map { it.name }.toSet()
        val forms = groups
            .filter { group -> allNames.containsAll(group.fingerprint) }
            .flatMap { it.forms }
            .filter { it.name in bareNames }
            .associateBy { it.name }
        if (forms.isEmpty()) return tools
        return tools.map { tool ->
            if (tool.metadataAvailable) {
                tool
            } else {
                forms[tool.name]?.copy(metadataAvailable = true, metadataComplete = true) ?: tool
            }
        }
    }

    private fun todoContentParameter() = AgentToolParameter(
        name = "content",
        type = "string",
        required = true,
        label = "To-do",
        description = "Type the item exactly as it appears on the list."
    )
}
