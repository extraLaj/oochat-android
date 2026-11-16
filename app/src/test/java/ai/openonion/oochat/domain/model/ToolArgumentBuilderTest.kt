package ai.openonion.oochat.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolArgumentBuilderTest {

    @Test
    fun `builds text number integer boolean and choice values as typed JSON`() {
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(
                    AgentToolParameter("title", type = "text", required = true),
                    AgentToolParameter("count", type = "integer"),
                    AgentToolParameter("ratio", type = "number"),
                    AgentToolParameter("enabled", type = "boolean"),
                    AgentToolParameter("mode", type = "choice", choices = listOf("fast", "safe"))
                )
            ),
            values = mapOf(
                "title" to "Report",
                "count" to "12",
                "ratio" to "2.50",
                "enabled" to "yes",
                "mode" to "safe"
            )
        )

        assertEquals(
            Json.parseToJsonElement(
                """{"title":"Report","count":12,"ratio":2.50,"enabled":true,"mode":"safe"}"""
            ),
            success(result)
        )
    }

    @Test
    fun `omits blank optional fields and reports missing required fields`() {
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(
                    AgentToolParameter("required", type = "string", required = true),
                    AgentToolParameter("optional", type = "string")
                )
            ),
            values = mapOf("required" to "   ", "optional" to "")
        )

        val invalid = invalid(result)
        assertEquals(listOf("required"), invalid.errors.map { it.parameterName })
        assertEquals("This field is required", invalid.errors.single().message)
    }

    @Test
    fun `collects errors for invalid numbers booleans and choices`() {
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(
                    AgentToolParameter("whole", type = "integer"),
                    AgentToolParameter("amount", type = "number"),
                    AgentToolParameter("enabled", type = "boolean"),
                    AgentToolParameter("mode", type = "choice", choices = listOf("fast", "safe"))
                )
            ),
            values = mapOf(
                "whole" to "1.5",
                "amount" to "not-a-number",
                "enabled" to "maybe",
                "mode" to "unknown"
            )
        )

        val invalid = invalid(result)
        assertEquals(
            mapOf(
                "whole" to "Enter a whole number",
                "amount" to "Enter a valid number",
                "enabled" to "Choose yes or no",
                "mode" to "Choose one of the available options"
            ),
            invalid.errors.associate { it.parameterName!! to it.message }
        )
    }

    @Test
    fun `rejects quoted numbers and boolean literals for numeric fields`() {
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(
                    AgentToolParameter("amount", type = "number"),
                    AgentToolParameter("whole", type = "integer")
                )
            ),
            values = mapOf("amount" to "\"12\"", "whole" to "true")
        )

        assertEquals(
            mapOf(
                "amount" to "Enter a valid number",
                "whole" to "Enter a whole number"
            ),
            invalid(result).errors.associate { it.parameterName!! to it.message }
        )
    }

    @Test
    fun `accepts large numeric values without converting them to floating point`() {
        val value = "9".repeat(400)
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(AgentToolParameter("amount", type = "number"))
            ),
            values = mapOf("amount" to value)
        )

        assertEquals(value, success(result)["amount"].toString())
    }

    @Test
    fun `canonicalizes accepted numeric text to valid JSON numbers`() {
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(
                    AgentToolParameter("plus", type = "integer"),
                    AgentToolParameter("leading_zero", type = "integer"),
                    AgentToolParameter("fraction", type = "number"),
                    AgentToolParameter("trailing_decimal", type = "number"),
                    AgentToolParameter("scientific_integer", type = "integer")
                )
            ),
            values = mapOf(
                "plus" to "+5",
                "leading_zero" to "05",
                "fraction" to ".5",
                "trailing_decimal" to "5.",
                "scientific_integer" to "1e3"
            )
        )

        assertEquals(
            """{"plus":5,"leading_zero":5,"fraction":0.5,"trailing_decimal":5,"scientific_integer":1000}""",
            success(result).toString()
        )
    }

    @Test
    fun `accepts case insensitive yes and no while emitting booleans`() {
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(
                    AgentToolParameter("enabled", type = "boolean"),
                    AgentToolParameter("visible", type = "bool")
                )
            ),
            values = mapOf("enabled" to "YES", "visible" to "No")
        )

        assertEquals(
            Json.parseToJsonElement("""{"enabled":true,"visible":false}"""),
            success(result)
        )
    }

    @Test
    fun `matches choices exactly after trimming and rejects unknown choices`() {
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(
                    AgentToolParameter("mode", type = "choice", choices = listOf("fast", "safe"))
                )
            ),
            values = mapOf("mode" to " safe ")
        )

        assertEquals(
            Json.parseToJsonElement("""{"mode":"safe"}"""),
            success(result)
        )
    }

    @Test
    fun `infers a choice from a choice list and ignores unknown form values`() {
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(
                    AgentToolParameter("mode", type = null, choices = listOf("fast", "safe"))
                )
            ),
            values = mapOf("mode" to "fast", "oldField" to "stale")
        )

        assertEquals(
            Json.parseToJsonElement("""{"mode":"fast"}"""),
            success(result)
        )
    }

    @Test
    fun `rejects unsupported or missing parameter types`() {
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(
                    AgentToolParameter("object", type = "object"),
                    AgentToolParameter("missing", type = null)
                )
            ),
            values = mapOf("object" to "value", "missing" to "value")
        )

        val invalid = invalid(result)
        assertEquals(
            mapOf(
                "object" to "This input type is not supported",
                "missing" to "This input does not have a supported type"
            ),
            invalid.errors.associate { it.parameterName!! to it.message }
        )
    }

    @Test
    fun `rejects duplicate and unnamed metadata instead of overwriting JSON`() {
        val result = ToolArgumentBuilder.build(
            tool = AgentTool(
                name = "demo",
                parameters = listOf(
                    AgentToolParameter("same", type = "string"),
                    AgentToolParameter("same", type = "string"),
                    AgentToolParameter("", type = "string")
                )
            ),
            values = emptyMap()
        )

        val invalid = invalid(result)
        assertTrue(invalid.errors.any { it.parameterName == "same" })
        assertTrue(invalid.errors.any { it.parameterName == null })
    }

    private fun success(result: ToolArgumentBuildResult): kotlinx.serialization.json.JsonObject {
        assertTrue("expected valid arguments: $result", result is ToolArgumentBuildResult.Success)
        return (result as ToolArgumentBuildResult.Success).arguments
    }

    private fun invalid(result: ToolArgumentBuildResult): ToolArgumentBuildResult.Invalid {
        assertTrue("expected invalid arguments: $result", result is ToolArgumentBuildResult.Invalid)
        return result as ToolArgumentBuildResult.Invalid
    }
}
