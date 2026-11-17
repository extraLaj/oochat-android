package ai.openonion.oochat.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * W16AAPPLE-127: every screen's content column stops at the one shared [ContentWidth.Max]. The
 * chat, its empty card, a Work Room, the forms and a Rem page had five caps of their own (600, 640,
 * 680 and 720dp), so a tablet's column moved from screen to screen. A breakpoint (a `...Min...`
 * width a layout switches at) is not a cap and is left alone.
 */
class ContentWidthTest {

    private val sources: File = listOf("src/main/java", "app/src/main/java").map(::File).first { it.isDirectory }

    // `widthIn(max = 600.dp)`, or a `...Width = 720.dp` constant, at a column's scale (500dp up).
    private val ownCap = Regex("""widthIn\(max\s*=\s*\d+\.dp|\w*Width\w*\s*=\s*([5-9]\d\d|\d{4})\.dp""")

    @Test
    fun `no screen caps its content at a width of its own`() {
        val offenders = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.name.equals("DesignToken.kt") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val match = ownCap.find(line) ?: return@mapIndexedNotNull null
                    if ("Min" in match.value) null else "${file.name}:${index + 1}: ${line.trim()}"
                }
            }
            .toList()

        assertEquals("Use ContentWidth.Max instead:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }
}
