package com.liquidglass.messages.ui.chat.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.liquidglass.messages.data.model.MessageEffect
import com.liquidglass.messages.ui.theme.LiquidTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Wraps a message [content] (the bubble) and plays an iMessage-style "send with
 * effect" animation each time [trigger] changes. The first non-zero [trigger]
 * value plays the effect once on appearance; bumping [trigger] (e.g. tapping the
 * bubble to replay) plays it again.
 *
 * All effects are driven by Compose animation primitives (Animatable / Canvas);
 * nothing here allocates work when [effect] is [MessageEffect.NONE], so the
 * wrapper is essentially free for ordinary messages.
 *
 * Scale-based emphasis effects (BIG / SMALL) anchor at the bubble's bottom so the
 * bubble grows up out of the conversation, matching iMessage.
 */
@Composable
fun EffectedBubble(
    effect: MessageEffect,
    trigger: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Fast path: no overlay, no animation state, just render the bubble.
    if (effect == MessageEffect.NONE) {
        Box(modifier = modifier) { content() }
        return
    }

    val accent = LiquidTheme.colors.accent

    // Shared animatables. Each effect drives the subset it needs and leaves the
    // rest at their identity values, so a single graphicsLayer covers them all.
    val scale = remember { Animatable(1f) }
    val translateX = remember { Animatable(0f) }
    val translateY = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(1f) }

    // Overlay progress (0f..1f) for effects that draw extra geometry behind the
    // bubble: RIPPLE ring, EXPLODE particles, BLOOM glow. -1f means "inactive".
    var overlayProgress by remember { mutableFloatStateOf(-1f) }

    // Continuous phase for JITTER's trembling. 0f = settled.
    var jitterPhase by remember { mutableFloatStateOf(0f) }
    var jitterAmplitude by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect

        // Reset to identity before (re)playing.
        scale.snapTo(1f)
        translateX.snapTo(0f)
        translateY.snapTo(0f)
        contentAlpha.snapTo(1f)
        overlayProgress = -1f
        jitterPhase = 0f
        jitterAmplitude = 0f

        when (effect) {
            MessageEffect.NONE -> Unit

            // Pops in big, overshooting slightly, then springs back to rest.
            MessageEffect.BIG -> {
                scale.snapTo(1.9f)
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.45f,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
            }

            // Starts tiny, gently springs up to full size.
            MessageEffect.SMALL -> {
                scale.snapTo(0.35f)
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }

            // Six decaying horizontal oscillations — a vigorous shake.
            MessageEffect.SHAKE -> {
                val cycles = 6
                val per = 55
                repeat(cycles) { i ->
                    val decay = 1f - i / cycles.toFloat()
                    val amp = 18f * decay
                    translateX.animateTo(amp, tween(per, easing = LinearEasing))
                    translateX.animateTo(-amp, tween(per, easing = LinearEasing))
                }
                translateX.animateTo(0f, tween(per, easing = LinearEasing))
            }

            // Scale pulse plus an expanding, fading ring drawn behind the bubble.
            MessageEffect.RIPPLE -> {
                overlayProgress = 0f
                val ring = Animatable(0f)
                coroutineScope {
                    // Ring grows past the bubble and fades on its own coroutine.
                    launch {
                        ring.animateTo(1f, tween(620, easing = LinearEasing)) {
                            overlayProgress = value
                        }
                        overlayProgress = -1f
                    }
                    // Drive the bubble's own scale: 1.0 -> 1.12 -> 1.0.
                    launch {
                        scale.animateTo(1.12f, tween(180))
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow),
                        )
                    }
                }
            }

            // Bubble pops (scale + fade in) while a ring of particles flies out.
            MessageEffect.EXPLODE -> {
                overlayProgress = 0f
                contentAlpha.snapTo(0.3f)
                scale.snapTo(0.6f)
                coroutineScope {
                    launch {
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                        )
                    }
                    launch { contentAlpha.animateTo(1f, tween(220)) }
                    launch {
                        val burst = Animatable(0f)
                        burst.animateTo(1f, tween(620, easing = LinearEasing)) {
                            overlayProgress = value
                        }
                        overlayProgress = -1f
                    }
                }
            }

            // Soft accent glow blooms out behind the bubble as it scales in.
            MessageEffect.BLOOM -> {
                overlayProgress = 0f
                scale.snapTo(0.85f)
                coroutineScope {
                    launch {
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                        )
                    }
                    launch {
                        val glow = Animatable(0f)
                        glow.animateTo(1f, tween(900, easing = LinearEasing)) {
                            overlayProgress = value
                        }
                        overlayProgress = -1f
                    }
                }
            }

            // ~1.2s of small, organic trembling that fades out to rest.
            MessageEffect.JITTER -> {
                val driver = Animatable(0f)
                driver.animateTo(1f, tween(1200, easing = LinearEasing)) {
                    jitterPhase = value * 14f          // many small wobbles
                    jitterAmplitude = (1f - value) * 4f // decays to 0
                }
                jitterPhase = 0f
                jitterAmplitude = 0f
            }
        }
    }

    // JITTER feeds graphicsLayer through its own offsets (sin/cos of the phase),
    // independent of the translate animatables used by SHAKE.
    val jitterX = if (jitterAmplitude > 0f) sin(jitterPhase) * jitterAmplitude else 0f
    val jitterY = if (jitterAmplitude > 0f) cos(jitterPhase * 1.3f) * jitterAmplitude else 0f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .matchOverlay(effect, overlayProgress, accent)
                .graphicsLayer {
                    val s = scale.value
                    scaleX = s
                    scaleY = s
                    // BIG / SMALL grow from the bottom of the bubble.
                    transformOrigin = when (effect) {
                        MessageEffect.BIG, MessageEffect.SMALL ->
                            TransformOrigin(0.5f, 1f)
                        else -> TransformOrigin.Center
                    }
                    translationX = translateX.value + jitterX
                    translationY = translateY.value + jitterY
                    alpha = contentAlpha.value
                },
        ) {
            content()
        }
    }
}

/**
 * Attaches the behind-the-bubble overlay (ring / particles / glow) for the
 * effects that need one. When [progress] < 0 the modifier is a no-op, so this is
 * free for scale/translate-only effects and at rest.
 */
private fun Modifier.matchOverlay(
    effect: MessageEffect,
    progress: Float,
    accent: Color,
): Modifier {
    if (progress < 0f) return this
    return when (effect) {
        MessageEffect.RIPPLE -> drawBehind { drawRipple(progress, accent) }
        MessageEffect.EXPLODE -> drawBehind { drawExplosion(progress, accent) }
        MessageEffect.BLOOM -> drawBehind { drawBloom(progress, accent) }
        else -> this
    }
}

/** Expanding translucent ring that grows past the bubble and fades out. */
private fun DrawScope.drawRipple(progress: Float, accent: Color) {
    val maxDim = max(size.width, size.height)
    val radius = (maxDim * 0.5f) + maxDim * 0.6f * progress
    val alpha = (1f - progress).coerceIn(0f, 1f) * 0.55f
    drawCircle(
        color = accent.copy(alpha = alpha),
        radius = radius,
        center = size.center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
    )
}

/** Deterministic 14-dot particle burst flying radially outward, fading as it goes. */
private fun DrawScope.drawExplosion(progress: Float, accent: Color) {
    val count = 14
    val center = size.center
    val spread = max(size.width, size.height) * 0.85f
    val alpha = (1f - progress).coerceIn(0f, 1f)
    val dotRadius = (1f - progress).coerceIn(0f, 1f) * 5.dp.toPx() + 1f
    for (i in 0 until count) {
        val angle = Math.toRadians((i * (360.0 / count))).toFloat()
        val dist = spread * progress
        val x = center.x + cos(angle) * dist
        val y = center.y + sin(angle) * dist
        drawCircle(
            color = accent.copy(alpha = alpha),
            radius = dotRadius,
            center = Offset(x, y),
        )
    }
}

/** Soft radial accent glow that expands behind the bubble and fades. */
private fun DrawScope.drawBloom(progress: Float, accent: Color) {
    val maxDim = max(size.width, size.height)
    val radius = (maxDim * 0.45f) + maxDim * 0.85f * progress
    // Peak around the first third, then ease away.
    val envelope = if (progress < 0.33f) progress / 0.33f else (1f - progress) / 0.67f
    val alpha = envelope.coerceIn(0f, 1f) * 0.45f
    if (radius <= 0f || alpha <= 0f) return
    val gradient = androidx.compose.ui.graphics.Brush.radialGradient(
        colors = listOf(accent.copy(alpha = alpha), Color.Transparent),
        center = size.center,
        radius = max(radius, 1f),
    )
    drawCircle(brush = gradient, radius = radius, center = size.center)
}
