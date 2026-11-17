package ai.openonion.oochat.ui.theme

import ai.openonion.oochat.ui.components.appCardBorderColor
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "The agent needs you" is one look on Chat (W16AAPPLE-125): the Work Room card and the approval
 * card under it both draw [ai.openonion.oochat.ui.components.AppCard], so neither drifts to a fill
 * or border of its own.
 */
class AgentCardStyleTest {

    private val cardFiles = setOf("WorkRoomCard.kt", "WorkRoomApprovalCard.kt")

    @Test
    fun `the Work Room and approval cards are both AppCards`() {
        val files = SourceScan.kotlinFiles().filter { it.name in cardFiles }.toList()
        assertEquals(cardFiles, files.map { it.name }.toSet())

        files.forEach { file ->
            val text = file.readText()
            assertTrue("${file.name} draws an AppCard", Regex("""(?<![A-Za-z])AppCard\(""").containsMatchIn(text))
            assertFalse("${file.name} builds a Card of its own", Regex("""(?<![A-Za-z.])Card\(""").containsMatchIn(text))
        }
    }

    @Test
    fun `a waiting card keeps a primary border and an answered one the quiet outline`() {
        val colors = lightColorScheme()

        assertEquals(colors.outlineVariant, appCardBorderColor(highlighted = false, colors = colors))
        val waiting = appCardBorderColor(highlighted = true, colors = colors)
        assertNotEquals(colors.outlineVariant, waiting)
        assertEquals(colors.primary.copy(alpha = 0.30f), waiting)
    }

    @Test
    fun `the approval card is highlighted exactly while it has no decision`() {
        val source = SourceScan.kotlinFiles().single { it.name == "WorkRoomApprovalCard.kt" }.readText()

        assertTrue(source.contains("highlighted = item.decision == null"))
    }
}
