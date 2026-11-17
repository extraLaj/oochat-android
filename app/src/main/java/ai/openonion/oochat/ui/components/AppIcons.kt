package ai.openonion.oochat.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The two icons the drawer design draws that Material's set has no match for (W16AAPPLE-114).
 * Both are outlines on a 24dp grid, to sit beside Material's outlined icons, and take their
 * colour from the `Icon` that shows them.
 */
object AppIcons {

    /** A pen over a square sheet: start a new chat. */
    val NewChat: ImageVector by lazy {
        outline(
            "NewChat",
            "M11 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5",
            "M17.5 3.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4z"
        )
    }

    /** A speech bubble with a cross in it: clear a chat's messages. */
    val ClearChat: ImageVector by lazy {
        outline(
            "ClearChat",
            "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z",
            "M9.5 7.5l5 5",
            "M14.5 7.5l-5 5"
        )
    }

    private fun outline(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { path ->
            builder.addPath(
                pathData = addPathNodes(path),
                // Any opaque colour: the Icon's tint replaces it.
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }
}
