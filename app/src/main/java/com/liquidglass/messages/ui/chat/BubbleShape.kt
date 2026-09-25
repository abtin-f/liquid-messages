package com.liquidglass.messages.ui.chat

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Width reserved on the sender's side of every bubble for the tail. */
val BubbleTailWidth: Dp = 7.dp

/** iOS Messages bubble corner radius (a one-line bubble becomes a capsule). */
private val BubbleRadius: Dp = 18.dp

/** Height above the baseline where the tail leaves the bubble's edge. */
private val TailRise: Dp = 10.dp

/**
 * The iMessage bubble silhouette as ONE continuous path, so the tail flows out
 * of the body with no seam.
 *
 * Geometry (drawn for an outgoing bubble, mirrored for incoming) follows the
 * classic iOS tail: the edge drops straight, then sweeps out along a concave
 * arc to a tip on the baseline; the underside returns with a shallow notch
 * before settling onto the body's bottom edge.
 *
 * Every bubble reserves [BubbleTailWidth] on the sender side (tail or not) so a
 * run keeps one straight edge; only the last bubble of a run shows the tail.
 */
class BubbleShape(
    private val outgoing: Boolean,
    private val hasTail: Boolean,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val u = density.density // 1dp in px
        val t = BubbleTailWidth.value * u
        val rise = TailRise.value * u
        val r = (BubbleRadius.value * u).coerceAtMost(h / 2f)
        val k = 0.5523f // quarter-circle cubic approximation

        val right = w - t
        // Mirror x for incoming bubbles.
        fun x(v: Float) = if (outgoing) v else w - v

        val p = Path()
        p.moveTo(x(r), 0f)
        p.lineTo(x(right - r), 0f)
        p.cubicTo(x(right - r + r * k), 0f, x(right), r - r * k, x(right), r)

        if (hasTail) {
            p.lineTo(x(right), (h - rise).coerceAtLeast(r))
            // Concave outer edge sweeping out to the tip on the baseline.
            p.cubicTo(x(right), h - 0.45f * rise, x(right + 0.45f * t), h - 0.02f * u, x(right + t), h)
            // Underside: back in with a shallow notch…
            p.cubicTo(x(right + 3f * u), h, x(right + 0.5f * u), h - 1.3f * u, x(right - 2.5f * u), h - 1.7f * u)
            // …settling onto the body's bottom edge.
            p.quadraticBezierTo(x(right - 5.5f * u), h, x(right - 10f * u), h)
        } else {
            p.lineTo(x(right), h - r)
            p.cubicTo(x(right), h - r + r * k, x(right - r + r * k), h, x(right - r), h)
        }

        p.lineTo(x(r), h)
        p.cubicTo(x(r - r * k), h, x(0f), h - r + r * k, x(0f), h - r)
        p.lineTo(x(0f), r)
        p.cubicTo(x(0f), r - r * k, x(r - r * k), 0f, x(r), 0f)
        p.close()
        return Outline.Generic(p)
    }

    override fun equals(other: Any?): Boolean =
        other is BubbleShape && other.outgoing == outgoing && other.hasTail == hasTail

    override fun hashCode(): Int = (if (outgoing) 2 else 0) + if (hasTail) 1 else 0
}
