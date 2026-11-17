package ai.openonion.oochat.domain.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInToolFormsTest {

    /** The bare names ConnectOnion 1.8.8's default co-ai agent sends. */
    private val coAiNames = listOf(
        "edit", "glob", "grep", "multi_edit", "read_file", "write", "bash",
        "add", "clear", "complete", "list", "remove", "start", "update",
        "skill", "load_guide", "ask_user"
    )

    private fun bare(vararg names: String) = names.map { AgentTool(name = it) }

    private fun List<AgentTool>.named(name: String) = first { it.name == name }

    @Test
    fun `fills in forms for the standard ConnectOnion tools`() {
        val tools = BuiltInToolForms.apply(bare(*coAiNames.toTypedArray()))

        val formNames = tools.filter { it.isFormAvailable }.map { it.name }.toSet()
        assertEquals(
            setOf("read_file", "glob", "grep", "list", "add", "start", "complete", "remove", "clear"),
            formNames
        )
        assertEquals(coAiNames, tools.map { it.name })
    }

    @Test
    fun `tools that run commands or change files never get a form`() {
        val tools = BuiltInToolForms.apply(bare(*coAiNames.toTypedArray()))

        listOf("bash", "write", "edit", "multi_edit", "update", "skill", "load_guide", "ask_user")
            .forEach { name ->
                val tool = tools.named(name)
                assertFalse("$name should stay developer-only", tool.isFormAvailable)
                assertFalse(tool.metadataAvailable)
            }
    }

    @Test
    fun `parameter names match the Host's own tool signature`() {
        val tools = BuiltInToolForms.apply(bare(*coAiNames.toTypedArray()))

        assertEquals(listOf("path", "offset", "limit"), tools.named("read_file").parameters.map { it.name })
        assertEquals(listOf("pattern", "path"), tools.named("glob").parameters.map { it.name })
        assertEquals(
            listOf("pattern", "path", "file_pattern", "output_mode", "ignore_case", "max_results"),
            tools.named("grep").parameters.map { it.name }
        )
        assertEquals(listOf("content", "active_form", "priority"), tools.named("add").parameters.map { it.name })
        assertTrue(tools.named("list").parameters.isEmpty())
        assertTrue(tools.all { tool -> tool.parameters.none { it.name == "summary" } })
    }

    @Test
    fun `a form builds the arguments the Host expects`() {
        val readFile = BuiltInToolForms.apply(bare(*coAiNames.toTypedArray())).named("read_file")

        val result = ToolArgumentBuilder.build(readFile, mapOf("path" to "notes/q3.md", "limit" to "40"))

        assertEquals(
            ToolArgumentBuildResult.Success(
                buildJsonObject {
                    put("path", JsonPrimitive("notes/q3.md"))
                    put("limit", JsonPrimitive(40))
                }
            ),
            result
        )
    }

    @Test
    fun `deleting to-dos asks for confirmation`() {
        val tools = BuiltInToolForms.apply(bare(*coAiNames.toTypedArray()))

        assertTrue(tools.named("clear").requiresConfirmation)
        assertTrue(tools.named("remove").requiresConfirmation)
        assertFalse(tools.named("add").requiresConfirmation)
    }

    @Test
    fun `a generic name alone does not get a form without its whole group`() {
        val tools = BuiltInToolForms.apply(bare("list", "clear", "read_file", "search"))

        assertTrue(tools.none { it.isFormAvailable })
        assertTrue(tools.none { it.metadataAvailable })
    }

    @Test
    fun `each group is checked on its own`() {
        val tools = BuiltInToolForms.apply(
            bare("edit", "glob", "grep", "multi_edit", "read_file", "write", "list", "clear")
        )

        assertTrue(tools.named("read_file").isFormAvailable)
        assertFalse(tools.named("list").isFormAvailable)
        assertFalse(tools.named("clear").isFormAvailable)
    }

    @Test
    fun `a schema sent by the Host is kept as it is`() {
        val hostReadFile = AgentTool(
            name = "read_file",
            description = "Host description",
            parameters = listOf(AgentToolParameter(name = "file", type = "string", required = true)),
            metadataAvailable = true
        )
        val input = bare(*coAiNames.toTypedArray()).map { if (it.name == "read_file") hostReadFile else it }

        val tools = BuiltInToolForms.apply(input)

        assertSame(hostReadFile, tools.named("read_file"))
        assertNull(tools.named("read_file").displayName)
        assertTrue(tools.named("glob").isFormAvailable)
    }

    @Test
    fun `an agent without the standard tools is left unchanged`() {
        val input = bare("bash", "search")

        assertSame(input, BuiltInToolForms.apply(input))
    }
}
