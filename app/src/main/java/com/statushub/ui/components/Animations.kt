package com.statushub.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.statushub.ui.theme.BrandGradients
import kotlinx.coroutines.delay

/**
 * Fades, lifts and scales an item in when it first appears.
 * Only the first screenful is staggered; items scrolled into view later animate without delay.
 */
fun Modifier.animateEntrance(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    val liftPx = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(Unit) {
        if (index < 15) delay(index * 40L)
        progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * liftPx
        scaleX = 0.92f + 0.08f * p
        scaleY = 0.92f + 0.08f * p
    }
}

/** Shrinks slightly while pressed for a tactile feel */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.94f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Animated light sweep used for loading skeletons */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmerX"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(x * 600f, 0f),
            end = Offset(x * 600f + 400f, 400f)
        )
    )
}

/** Gentle up/down float, used for illustrations and empty states */
fun Modifier.floating(distance: Dp = 8.dp, durationMs: Int = 1800): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "float")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatOffset"
    )
    val px = with(LocalDensity.current) { distance.toPx() }
    graphicsLayer { translationY = offset * px }
}

/** Slowly breathing scale + alpha, used for halo rings behind illustrations */
fun Modifier.pulse(minScale: Float = 0.9f, maxScale: Float = 1.08f, durationMs: Int = 1600): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "pulse")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseT"
    )
    graphicsLayer {
        val s = minScale + (maxScale - minScale) * t
        scaleX = s
        scaleY = s
        alpha = 0.55f + 0.45f * (1f - t)
    }
}

enum class SaveState { IDLE, SAVING, SAVED }

/**
 * Round save button that animates ⬇ → spinner → ✓ (with a bouncy pop and a haptic tick on success)
 */
@Composable
fun AnimatedSaveButton(
    state: SaveState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    brush: Brush = BrandGradients.status
) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) {
        if (state == SaveState.SAVED) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction, 0.85f)
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = state == SaveState.IDLE,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = Spring.DampingRatioHighBouncy)) + fadeIn()) togetherWith
                    (scaleOut() + fadeOut())
            },
            label = "saveState"
        ) { s ->
            when (s) {
                SaveState.IDLE -> IconBadge(Icons.Rounded.Download, brush, size = size, iconSize = size * 0.56f)
                SaveState.SAVING -> Box(
                    Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(size * 0.56f))
                }
                SaveState.SAVED -> IconBadge(
                    Icons.Rounded.Check,
                    Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF16A34A))),
                    size = size,
                    iconSize = size * 0.56f
                )
            }
        }
    }
}
