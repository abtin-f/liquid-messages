package com.liquidglass.messages.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Line icons drawn to match SF Symbols' proportions (regular weight, rounded
 * caps and joins) on a 24-unit grid. Material icons are geometric and heavy;
 * these thin, round-ended strokes are a large part of why iOS looks like iOS.
 *
 * All are tinted by the caller (`Icon(tint = …)`); the stroke colour here is
 * just a placeholder that tinting replaces.
 */
object IosIcons {

    private fun icon(
        name: String,
        strokeWidth: Float = 2f,
        fill: Boolean = false,
        block: PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        if (fill) {
            path(fill = SolidColor(Color.Black), pathBuilder = block)
        } else {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }
    }.build()

    /** chevron.left — back button. */
    val ChevronLeft: ImageVector by lazy {
        icon("chevron.left", strokeWidth = 2.4f) {
            moveTo(15f, 4.5f); lineTo(7.5f, 12f); lineTo(15f, 19.5f)
        }
    }

    /** chevron.right — disclosure indicator (drawn small by callers). */
    val ChevronRight: ImageVector by lazy {
        icon("chevron.right", strokeWidth = 2.4f) {
            moveTo(9f, 4.5f); lineTo(16.5f, 12f); lineTo(9f, 19.5f)
        }
    }

    /** plus — composer "apps" button. */
    val Plus: ImageVector by lazy {
        icon("plus", strokeWidth = 2.1f) {
            moveTo(12f, 4.5f); lineTo(12f, 19.5f)
            moveTo(4.5f, 12f); lineTo(19.5f, 12f)
        }
    }

    /** plus.circle — add recipient. */
    val PlusCircle: ImageVector by lazy {
        icon("plus.circle", strokeWidth = 1.7f) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); close()
            moveTo(12f, 7.5f); lineTo(12f, 16.5f)
            moveTo(7.5f, 12f); lineTo(16.5f, 12f)
        }
    }

    /** arrowshape.turn.up.left — reply. */
    val Reply: ImageVector by lazy {
        icon("arrowshape.turn.up.left", strokeWidth = 1.9f) {
            moveTo(9.5f, 5.5f); lineTo(3.5f, 11f); lineTo(9.5f, 16.5f)
            moveTo(3.8f, 11f); lineTo(14f, 11f)
            curveTo(17.9f, 11f, 20.5f, 13.6f, 20.5f, 17.5f); lineTo(20.5f, 19f)
        }
    }

    /** arrow.up — send (drawn white inside a filled circle). */
    val ArrowUp: ImageVector by lazy {
        icon("arrow.up", strokeWidth = 2.6f) {
            moveTo(12f, 19f); lineTo(12f, 5.5f)
            moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
        }
    }

    /** mic — dictation. */
    val Mic: ImageVector by lazy {
        icon("mic", strokeWidth = 1.8f) {
            // Capsule body.
            moveTo(9f, 6.5f)
            arcTo(3f, 3f, 0f, false, true, 15f, 6.5f)
            lineTo(15f, 11.5f)
            arcTo(3f, 3f, 0f, false, true, 9f, 11.5f)
            close()
            // Cradle + stand.
            moveTo(5.8f, 11f)
            arcTo(6.2f, 6.2f, 0f, false, false, 18.2f, 11f)
            moveTo(12f, 17.3f); lineTo(12f, 20.5f)
        }
    }

    /** video — FaceTime affordance. */
    val Video: ImageVector by lazy {
        icon("video", strokeWidth = 1.7f) {
            moveTo(5f, 6.5f)
            lineTo(13.5f, 6.5f)
            arcTo(2f, 2f, 0f, false, true, 15.5f, 8.5f)
            lineTo(15.5f, 15.5f)
            arcTo(2f, 2f, 0f, false, true, 13.5f, 17.5f)
            lineTo(5f, 17.5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 15.5f)
            lineTo(3f, 8.5f)
            arcTo(2f, 2f, 0f, false, true, 5f, 6.5f)
            close()
            moveTo(15.5f, 10.3f); lineTo(20.2f, 7.6f); lineTo(20.2f, 16.4f); lineTo(15.5f, 13.7f)
        }
    }

    /** square.and.pencil — new message. */
    val Compose: ImageVector by lazy {
        icon("square.and.pencil", strokeWidth = 1.8f) {
            moveTo(11f, 4.5f)
            lineTo(6.5f, 4.5f)
            arcTo(2.5f, 2.5f, 0f, false, false, 4f, 7f)
            lineTo(4f, 17.5f)
            arcTo(2.5f, 2.5f, 0f, false, false, 6.5f, 20f)
            lineTo(17f, 20f)
            arcTo(2.5f, 2.5f, 0f, false, false, 19.5f, 17.5f)
            lineTo(19.5f, 13f)
            // Pencil.
            moveTo(18.2f, 3.6f)
            lineTo(20.4f, 5.8f)
            lineTo(12.2f, 14f)
            lineTo(9.3f, 14.7f)
            lineTo(10f, 11.8f)
            close()
        }
    }

    /** magnifyingglass — search. */
    val Search: ImageVector by lazy {
        icon("magnifyingglass", strokeWidth = 2f) {
            moveTo(10.5f, 4f)
            arcTo(6.5f, 6.5f, 0f, true, true, 10.49f, 4f)
            close()
            moveTo(15.3f, 15.3f); lineTo(20f, 20f)
        }
    }

    /** xmark.circle.fill — clear field (filled disc; the x is punched out by callers). */
    val Close: ImageVector by lazy {
        icon("xmark", strokeWidth = 2.2f) {
            moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
            moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
        }
    }

    /** ellipsis — "more" menu. */
    val Ellipsis: ImageVector by lazy {
        icon("ellipsis", fill = true) {
            moveTo(5.5f, 12f); arcTo(1.6f, 1.6f, 0f, true, true, 5.49f, 12f); close()
            moveTo(12f, 12f); arcTo(1.6f, 1.6f, 0f, true, true, 11.99f, 12f); close()
            moveTo(18.5f, 12f); arcTo(1.6f, 1.6f, 0f, true, true, 18.49f, 12f); close()
        }
    }

    /** person.fill — avatar placeholder for unknown numbers. */
    val PersonFill: ImageVector by lazy {
        icon("person.fill", fill = true) {
            moveTo(12f, 3.5f)
            arcTo(4.2f, 4.2f, 0f, true, true, 11.99f, 3.5f)
            close()
            moveTo(3.5f, 21f)
            curveTo(3.5f, 16.6f, 7.3f, 14f, 12f, 14f)
            curveTo(16.7f, 14f, 20.5f, 16.6f, 20.5f, 21f)
            close()
        }
    }

    /** trash — delete. */
    val Trash: ImageVector by lazy {
        icon("trash", strokeWidth = 1.7f) {
            moveTo(4f, 6.5f); lineTo(20f, 6.5f)
            moveTo(9f, 6.5f); lineTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(15f, 6.5f)
            moveTo(6f, 6.5f); lineTo(7f, 19f)
            arcTo(1.6f, 1.6f, 0f, false, false, 8.6f, 20.5f)
            lineTo(15.4f, 20.5f)
            arcTo(1.6f, 1.6f, 0f, false, false, 17f, 19f)
            lineTo(18f, 6.5f)
            moveTo(10f, 10f); lineTo(10.3f, 17f)
            moveTo(14f, 10f); lineTo(13.7f, 17f)
        }
    }

    /** camera.fill */
    val Camera: ImageVector by lazy {
        icon("camera.fill", fill = true) {
            moveTo(8.6f, 5f); lineTo(15.4f, 5f); lineTo(16.8f, 7f); lineTo(19f, 7f)
            arcTo(2f, 2f, 0f, false, true, 21f, 9f); lineTo(21f, 17f)
            arcTo(2f, 2f, 0f, false, true, 19f, 19f); lineTo(5f, 19f)
            arcTo(2f, 2f, 0f, false, true, 3f, 17f); lineTo(3f, 9f)
            arcTo(2f, 2f, 0f, false, true, 5f, 7f); lineTo(7.2f, 7f); close()
            // Lens (even-odd hole drawn as reversed circle).
            moveTo(12f, 9.3f); arcTo(3.6f, 3.6f, 0f, true, false, 12.01f, 9.3f); close()
        }
    }

    /** photo.on.rectangle — gallery. */
    val Photos: ImageVector by lazy {
        icon("photo", strokeWidth = 1.7f) {
            moveTo(5f, 5f); lineTo(19f, 5f)
            arcTo(2f, 2f, 0f, false, true, 21f, 7f); lineTo(21f, 17f)
            arcTo(2f, 2f, 0f, false, true, 19f, 19f); lineTo(5f, 19f)
            arcTo(2f, 2f, 0f, false, true, 3f, 17f); lineTo(3f, 7f)
            arcTo(2f, 2f, 0f, false, true, 5f, 5f); close()
            moveTo(3.5f, 16f); lineTo(8.5f, 11f); lineTo(13f, 15.5f); lineTo(15.5f, 13f); lineTo(20.5f, 18f)
            moveTo(16f, 8.2f); arcTo(1.3f, 1.3f, 0f, true, true, 15.99f, 8.2f)
        }
    }

    /** face.smiling — stickers. */
    val Smile: ImageVector by lazy {
        icon("face.smiling", strokeWidth = 1.7f) {
            moveTo(12f, 3.5f); arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f); close()
            moveTo(8.3f, 14.2f); curveTo(9.2f, 15.8f, 10.5f, 16.5f, 12f, 16.5f); curveTo(13.5f, 16.5f, 14.8f, 15.8f, 15.7f, 14.2f)
            moveTo(9.3f, 9.5f); lineTo(9.3f, 10.6f)
            moveTo(14.7f, 9.5f); lineTo(14.7f, 10.6f)
        }
    }

    /** doc — files. */
    val Doc: ImageVector by lazy {
        icon("doc", strokeWidth = 1.7f) {
            moveTo(6.5f, 3f); lineTo(13.5f, 3f); lineTo(19f, 8.5f); lineTo(19f, 19.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 17.5f, 21f); lineTo(6.5f, 21f)
            arcTo(1.5f, 1.5f, 0f, false, true, 5f, 19.5f); lineTo(5f, 4.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 6.5f, 3f); close()
            moveTo(13.5f, 3f); lineTo(13.5f, 8.5f); lineTo(19f, 8.5f)
        }
    }

    /** location.fill — share location. */
    val Location: ImageVector by lazy {
        icon("location.fill", fill = true) {
            moveTo(20.5f, 3.5f); lineTo(3.5f, 10.8f); lineTo(11f, 13f); lineTo(13.2f, 20.5f); close()
        }
    }

    /** person.crop.circle — contact card. */
    val PersonCircle: ImageVector by lazy {
        icon("person.crop.circle", strokeWidth = 1.7f) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); close()
            moveTo(12f, 6.8f); arcTo(3.1f, 3.1f, 0f, true, true, 11.99f, 6.8f); close()
            moveTo(6.2f, 18.3f); curveTo(7.3f, 15.9f, 9.4f, 14.7f, 12f, 14.7f); curveTo(14.6f, 14.7f, 16.7f, 15.9f, 17.8f, 18.3f)
        }
    }

    /** clock — send later. */
    val Clock: ImageVector by lazy {
        icon("clock", strokeWidth = 1.8f) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); close()
            moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
        }
    }

    /** sparkles — message effects. */
    val Sparkles: ImageVector by lazy {
        icon("sparkles", fill = true) {
            moveTo(10f, 3f); quadTo(11f, 9f, 17f, 10f); quadTo(11f, 11f, 10f, 17f); quadTo(9f, 11f, 3f, 10f); quadTo(9f, 9f, 10f, 3f); close()
            moveTo(18f, 14f); quadTo(18.5f, 17f, 21f, 17.5f); quadTo(18.5f, 18f, 18f, 21f); quadTo(17.5f, 18f, 15f, 17.5f); quadTo(17.5f, 17f, 18f, 14f); close()
        }
    }

    /** phone.fill */
    val Phone: ImageVector by lazy {
        icon("phone.fill", fill = true) {
            moveTo(6.6f, 3f); lineTo(9f, 3f); lineTo(10.4f, 7.3f); lineTo(8.3f, 9.1f)
            curveTo(9.4f, 11.5f, 12.3f, 14.5f, 14.9f, 15.7f)
            lineTo(16.7f, 13.6f); lineTo(21f, 15f); lineTo(21f, 17.4f)
            curveTo(21f, 19.4f, 19.4f, 21f, 17.4f, 21f)
            curveTo(9.5f, 20.4f, 3.6f, 14.5f, 3f, 6.6f)
            curveTo(3f, 4.6f, 4.6f, 3f, 6.6f, 3f); close()
        }
    }

    /** bell.fill — notifications / alerts. */
    val Bell: ImageVector by lazy {
        icon("bell.fill", fill = true) {
            moveTo(12f, 3f); curveTo(8.7f, 3f, 6.5f, 5.5f, 6.5f, 8.8f); lineTo(6.5f, 13f); lineTo(4.5f, 16.5f); lineTo(19.5f, 16.5f); lineTo(17.5f, 13f); lineTo(17.5f, 8.8f)
            curveTo(17.5f, 5.5f, 15.3f, 3f, 12f, 3f); close()
            moveTo(9.5f, 18f); curveTo(9.8f, 19.5f, 10.8f, 20.5f, 12f, 20.5f); curveTo(13.2f, 20.5f, 14.2f, 19.5f, 14.5f, 18f); close()
        }
    }

    /** bell.slash — hide alerts. */
    val BellSlash: ImageVector by lazy {
        icon("bell.slash", strokeWidth = 1.7f) {
            moveTo(6.5f, 13f); lineTo(6.5f, 8.8f); curveTo(6.5f, 5.5f, 8.7f, 3f, 12f, 3f); curveTo(15.3f, 3f, 17.5f, 5.5f, 17.5f, 8.8f); lineTo(17.5f, 13f); lineTo(19.5f, 16.5f); lineTo(4.5f, 16.5f); close()
            moveTo(4f, 4f); lineTo(20f, 20f)
        }
    }

    /** hand.raised — block. */
    val Block: ImageVector by lazy {
        icon("nosign", strokeWidth = 1.9f) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); close()
            moveTo(5.8f, 5.8f); lineTo(18.2f, 18.2f)
        }
    }

    /** link */
    val Link: ImageVector by lazy {
        icon("link", strokeWidth = 1.8f) {
            moveTo(10.5f, 13.5f); lineTo(13.5f, 10.5f)
            moveTo(9f, 11f); lineTo(6.5f, 13.5f); arcTo(3f, 3f, 0f, false, false, 10.5f, 17.5f); lineTo(13f, 15f)
            moveTo(15f, 13f); lineTo(17.5f, 10.5f); arcTo(3f, 3f, 0f, false, false, 13.5f, 6.5f); lineTo(11f, 9f)
        }
    }

    /** gearshape.fill — settings. */
    val Gear: ImageVector by lazy {
        icon("gear", strokeWidth = 1.7f) {
            moveTo(12f, 8.8f); arcTo(3.2f, 3.2f, 0f, true, true, 11.99f, 8.8f); close()
            moveTo(10.3f, 3f); lineTo(13.7f, 3f); lineTo(14.2f, 5.4f); lineTo(16.1f, 6.5f); lineTo(18.4f, 5.7f); lineTo(20.1f, 8.6f); lineTo(18.3f, 10.2f)
            lineTo(18.3f, 13.8f); lineTo(20.1f, 15.4f); lineTo(18.4f, 18.3f); lineTo(16.1f, 17.5f); lineTo(14.2f, 18.6f); lineTo(13.7f, 21f); lineTo(10.3f, 21f)
            lineTo(9.8f, 18.6f); lineTo(7.9f, 17.5f); lineTo(5.6f, 18.3f); lineTo(3.9f, 15.4f); lineTo(5.7f, 13.8f); lineTo(5.7f, 10.2f); lineTo(3.9f, 8.6f)
            lineTo(5.6f, 5.7f); lineTo(7.9f, 6.5f); lineTo(9.8f, 5.4f); close()
        }
    }

    /** checkmark */
    val Check: ImageVector by lazy {
        icon("checkmark", strokeWidth = 2.4f) {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f)
        }
    }

    /** info.circle */
    val Info: ImageVector by lazy {
        icon("info.circle", strokeWidth = 1.8f) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); close()
            moveTo(12f, 11f); lineTo(12f, 16.5f)
            moveTo(12f, 7.6f); lineTo(12f, 7.7f)
        }
    }

    /** message.fill — bubble. */
    val Bubble: ImageVector by lazy {
        icon("message.fill", fill = true) {
            moveTo(12f, 3.5f); curveTo(6.8f, 3.5f, 2.8f, 7f, 2.8f, 11.3f); curveTo(2.8f, 13.7f, 4f, 15.8f, 6f, 17.2f)
            curveTo(5.8f, 18.6f, 5f, 19.8f, 3.8f, 20.6f); curveTo(6.2f, 20.7f, 8.2f, 19.8f, 9.4f, 18.8f)
            curveTo(10.2f, 19f, 11.1f, 19.1f, 12f, 19.1f); curveTo(17.2f, 19.1f, 21.2f, 15.6f, 21.2f, 11.3f); curveTo(21.2f, 7f, 17.2f, 3.5f, 12f, 3.5f); close()
        }
    }

    /** textformat.123 — character count. */
    val TextCount: ImageVector by lazy {
        icon("textformat.123", strokeWidth = 1.7f) {
            moveTo(3f, 17f); lineTo(6.5f, 7f); lineTo(10f, 17f)
            moveTo(4.2f, 13.5f); lineTo(8.8f, 13.5f)
            moveTo(13f, 9f); lineTo(15f, 7.5f); lineTo(15f, 17f)
            moveTo(18f, 8.5f); curveTo(18.5f, 7.3f, 21f, 7.3f, 21f, 9.3f); curveTo(21f, 11.3f, 18f, 13.8f, 18f, 17f); lineTo(21.2f, 17f)
        }
    }

    /** doc.on.doc — copy. */
    val Copy: ImageVector by lazy {
        icon("doc.on.doc", strokeWidth = 1.7f) {
            moveTo(9f, 7f); lineTo(9f, 5.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 10.5f, 4f)
            lineTo(18.5f, 4f)
            arcTo(1.5f, 1.5f, 0f, false, true, 20f, 5.5f)
            lineTo(20f, 15.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 18.5f, 17f)
            lineTo(17f, 17f)
            moveTo(5.5f, 7f)
            lineTo(13.5f, 7f)
            arcTo(1.5f, 1.5f, 0f, false, true, 15f, 8.5f)
            lineTo(15f, 18.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 13.5f, 20f)
            lineTo(5.5f, 20f)
            arcTo(1.5f, 1.5f, 0f, false, true, 4f, 18.5f)
            lineTo(4f, 8.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 5.5f, 7f)
            close()
        }
    }

    /** pin.fill — pinned conversations. */
    val Pin: ImageVector by lazy {
        icon("pin.fill", fill = true) {
            moveTo(8f, 3f); lineTo(16f, 3f); lineTo(15f, 5f); lineTo(15f, 10f); lineTo(18f, 13.5f); lineTo(18f, 15f); lineTo(12.8f, 15f)
            lineTo(12f, 22f); lineTo(11.2f, 15f); lineTo(6f, 15f); lineTo(6f, 13.5f); lineTo(9f, 10f); lineTo(9f, 5f); close()
        }
    }

    /** pin.slash — unpin. */
    val PinSlash: ImageVector by lazy {
        icon("pin.slash", strokeWidth = 1.7f) {
            moveTo(8f, 3f); lineTo(16f, 3f); lineTo(15f, 5f); lineTo(15f, 10f); lineTo(18f, 13.5f); lineTo(18f, 15f); lineTo(6f, 15f); lineTo(6f, 13.5f); lineTo(9f, 10f); lineTo(9f, 5f); close()
            moveTo(12f, 15f); lineTo(12f, 21.5f)
            moveTo(4f, 3.5f); lineTo(20f, 19.5f)
        }
    }

    /** line.3.horizontal.decrease — filters. */
    val Filter: ImageVector by lazy {
        icon("line.3.horizontal.decrease", strokeWidth = 2f) {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(7f, 12f); lineTo(17f, 12f)
            moveTo(10f, 17f); lineTo(14f, 17f)
        }
    }

    /** circle.lefthalf.filled — appearance. */
    val Appearance: ImageVector by lazy {
        icon("circle.lefthalf.filled", fill = true) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); close()
            moveTo(12f, 4.8f); lineTo(12f, 19.2f); arcTo(7.2f, 7.2f, 0f, false, false, 12f, 4.8f); close()
        }
    }

    /** textformat.size — text size. */
    val TextSize: ImageVector by lazy {
        icon("textformat.size", strokeWidth = 1.9f) {
            moveTo(3f, 19f); lineTo(8.5f, 5f); lineTo(14f, 19f)
            moveTo(5f, 14f); lineTo(12f, 14f)
            moveTo(14.5f, 19f); lineTo(17.5f, 11f); lineTo(20.5f, 19f)
            moveTo(15.6f, 16.2f); lineTo(19.4f, 16.2f)
        }
    }

    /** paintbrush.fill — bubble colour. */
    val Paintbrush: ImageVector by lazy {
        icon("paintbrush.fill", fill = true) {
            moveTo(20.5f, 3.5f); curveTo(19.5f, 2.6f, 18.3f, 3f, 17.3f, 4f); lineTo(10.5f, 11.8f); lineTo(12.2f, 13.5f); lineTo(20f, 6.7f)
            curveTo(21f, 5.7f, 21.4f, 4.5f, 20.5f, 3.5f); close()
            moveTo(9.3f, 13f); curveTo(7f, 12.8f, 5.3f, 14.5f, 5f, 16.5f); curveTo(4.8f, 18f, 4f, 19.2f, 3f, 19.8f)
            curveTo(6f, 21.2f, 10.7f, 20.5f, 11f, 15f); close()
        }
    }

    /** photo.on.rectangle — wallpaper. */
    val Wallpaper: ImageVector by lazy {
        icon("photo", strokeWidth = 1.7f) {
            moveTo(5f, 4f); lineTo(19f, 4f); arcTo(2f, 2f, 0f, false, true, 21f, 6f); lineTo(21f, 18f); arcTo(2f, 2f, 0f, false, true, 19f, 20f)
            lineTo(5f, 20f); arcTo(2f, 2f, 0f, false, true, 3f, 18f); lineTo(3f, 6f); arcTo(2f, 2f, 0f, false, true, 5f, 4f); close()
            moveTo(3.5f, 17f); lineTo(9f, 11.5f); lineTo(13f, 15.5f); lineTo(15.5f, 13f); lineTo(20.5f, 18f)
            moveTo(16f, 8f); lineTo(16f, 8.1f)
        }
    }

    /** eye — previews. */
    val Eye: ImageVector by lazy {
        icon("eye", strokeWidth = 1.7f) {
            moveTo(2.5f, 12f); curveTo(4.5f, 7.8f, 8f, 5.5f, 12f, 5.5f); curveTo(16f, 5.5f, 19.5f, 7.8f, 21.5f, 12f)
            curveTo(19.5f, 16.2f, 16f, 18.5f, 12f, 18.5f); curveTo(8f, 18.5f, 4.5f, 16.2f, 2.5f, 12f); close()
            moveTo(12f, 9f); arcTo(3f, 3f, 0f, true, true, 11.99f, 9f); close()
        }
    }

    /** archivebox — message history. */
    val Archive: ImageVector by lazy {
        icon("archivebox", strokeWidth = 1.7f) {
            moveTo(3f, 4.5f); lineTo(21f, 4.5f); lineTo(21f, 8.5f); lineTo(3f, 8.5f); close()
            moveTo(4.5f, 8.5f); lineTo(4.5f, 19.5f); lineTo(19.5f, 19.5f); lineTo(19.5f, 8.5f)
            moveTo(9.5f, 12f); lineTo(14.5f, 12f)
        }
    }

    /** envelope.badge — mark unread / read. */
    val Envelope: ImageVector by lazy {
        icon("envelope", strokeWidth = 1.7f) {
            moveTo(4f, 5.5f); lineTo(20f, 5.5f); arcTo(1.5f, 1.5f, 0f, false, true, 21.5f, 7f); lineTo(21.5f, 17f); arcTo(1.5f, 1.5f, 0f, false, true, 20f, 18.5f)
            lineTo(4f, 18.5f); arcTo(1.5f, 1.5f, 0f, false, true, 2.5f, 17f); lineTo(2.5f, 7f); arcTo(1.5f, 1.5f, 0f, false, true, 4f, 5.5f); close()
            moveTo(3f, 6.5f); lineTo(12f, 13f); lineTo(21f, 6.5f)
        }
    }

    /** person.crop.circle.badge.questionmark — unknown senders. */
    val PersonQuestion: ImageVector by lazy {
        icon("person.crop.circle.badge.questionmark", strokeWidth = 1.7f) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); close()
            moveTo(9.5f, 9.5f); curveTo(9.5f, 8f, 10.6f, 7f, 12f, 7f); curveTo(13.4f, 7f, 14.5f, 8f, 14.5f, 9.3f); curveTo(14.5f, 11.2f, 12f, 11.3f, 12f, 13.3f)
            moveTo(12f, 16.6f); lineTo(12f, 16.7f)
        }
    }

    /** speaker.wave.2 — sounds. */
    val Speaker: ImageVector by lazy {
        icon("speaker.wave.2", strokeWidth = 1.7f) {
            moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f); lineTo(12f, 18.5f); lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close()
            moveTo(15f, 9f); curveTo(16.2f, 10.5f, 16.2f, 13.5f, 15f, 15f)
            moveTo(17.8f, 6.5f); curveTo(20.4f, 9.5f, 20.4f, 14.5f, 17.8f, 17.5f)
        }
    }

    /** hand.tap — haptics. */
    val Haptics: ImageVector by lazy {
        icon("iphone.radiowaves", strokeWidth = 1.7f) {
            moveTo(9f, 3.5f); lineTo(15f, 3.5f); arcTo(1.5f, 1.5f, 0f, false, true, 16.5f, 5f); lineTo(16.5f, 19f); arcTo(1.5f, 1.5f, 0f, false, true, 15f, 20.5f)
            lineTo(9f, 20.5f); arcTo(1.5f, 1.5f, 0f, false, true, 7.5f, 19f); lineTo(7.5f, 5f); arcTo(1.5f, 1.5f, 0f, false, true, 9f, 3.5f); close()
            moveTo(4.5f, 8.5f); curveTo(3.5f, 10.5f, 3.5f, 13.5f, 4.5f, 15.5f)
            moveTo(19.5f, 8.5f); curveTo(20.5f, 10.5f, 20.5f, 13.5f, 19.5f, 15.5f)
        }
    }
}
