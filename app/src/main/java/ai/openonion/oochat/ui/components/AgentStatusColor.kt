package ai.openonion.oochat.ui.components

import ai.openonion.oochat.domain.model.AgentStatus
import ai.openonion.oochat.ui.theme.PillShape
import ai.openonion.oochat.ui.theme.spacing
import ai.openonion.oochat.ui.theme.statusColors
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for status color, shared by the agent list and nav
 * drawer so the two can't drift. Each state has a hue of its own (W16AAPPLE-114):
 * blue while the agent works, green once connected, amber on the way there,
 * red for a failure and grey when it is switched off. The hue is never the only
 * signal: see [AgentStatusChip], which gives each an icon and a word as well.
 */
@Composable
fun agentStatusColor(status: AgentStatus): Color = when (status) {
    AgentStatus.Active -> MaterialTheme.statusColors.active
    AgentStatus.Connected -> MaterialTheme.statusColors.success
    AgentStatus.Connecting -> MaterialTheme.statusColors.connecting
    is AgentStatus.Error -> MaterialTheme.colorScheme.error
    AgentStatus.Disabled -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The pale fill a status is written on, in [AgentStatusChip]. */
@Composable
fun agentStatusContainerColor(status: AgentStatus): Color = when (status) {
    AgentStatus.Active -> MaterialTheme.statusColors.activeContainer
    AgentStatus.Connected -> MaterialTheme.statusColors.successContainer
    AgentStatus.Connecting -> MaterialTheme.statusColors.connectingContainer
    is AgentStatus.Error -> MaterialTheme.colorScheme.errorContainer
    AgentStatus.Disabled -> MaterialTheme.colorScheme.surfaceContainerHigh
}

/** The word for a status, the same wherever it is said. */
fun agentStatusLabel(status: AgentStatus): String = when (status) {
    AgentStatus.Active -> "Active"
    AgentStatus.Connected -> "Connected"
    AgentStatus.Connecting -> "Connecting"
    is AgentStatus.Error -> "Error"
    AgentStatus.Disabled -> "Disabled"
}

/**
 * An agent's status as a chip: a shape and a word on a tint, so it reads without colour
 * (W16AAPPLE-114). The shapes differ as much as the hues do: a ring with a dot, a solid dot, a
 * broken ring that turns, a warning triangle, a barred circle.
 *
 * The chip says only its status to TalkBack. A row that names the agent beside it merges the two
 * and is read as "name, status".
 */
@Composable
fun AgentStatusChip(status: AgentStatus, modifier: Modifier = Modifier) {
    val color = agentStatusColor(status)
    val label = agentStatusLabel(status)
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(agentStatusContainerColor(status))
            .padding(horizontal = MaterialTheme.spacing.sm2, vertical = MaterialTheme.spacing.xs)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs2)
    ) {
        AgentStatusGlyph(status = status, color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

/** The shape that goes with a status; see [AgentStatusChip]. */
@Composable
private fun AgentStatusGlyph(status: AgentStatus, color: Color) {
    val glyph = Modifier.size(12.dp)
    when (status) {
        AgentStatus.Active -> Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, tint = color, modifier = glyph)
        AgentStatus.Connected -> Box(modifier = glyph, contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        }
        AgentStatus.Connecting -> {
            val turn by rememberInfiniteTransition(label = "statusTurn").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
                label = "statusTurnAngle"
            )
            Canvas(modifier = glyph.graphicsLayer { rotationZ = turn }) {
                val stroke = 2.dp.toPx()
                val dash = 2.dp.toPx()
                drawCircle(
                    color = color,
                    radius = (size.minDimension - stroke) / 2 - 1.dp.toPx(),
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 1.4f))
                    )
                )
            }
        }
        is AgentStatus.Error -> Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = glyph)
        AgentStatus.Disabled -> Icon(Icons.Filled.Block, contentDescription = null, tint = color, modifier = glyph)
    }
}
