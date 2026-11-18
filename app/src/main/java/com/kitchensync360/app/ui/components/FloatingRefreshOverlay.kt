package com.kitchensync360.app.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kitchensync360.app.ui.theme.KsBorderStrong
import com.kitchensync360.app.ui.theme.KsGreenPrimary
import com.kitchensync360.app.ui.theme.KsGreenText
import com.kitchensync360.app.ui.theme.KsSurfaceElevated
import com.kitchensync360.app.ui.theme.KsTextPrimary
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val REFRESH_PREFS = "ks360_floating_refresh_prefs"
private const val KEY_X_RATIO = "x_ratio"
private const val KEY_Y_RATIO = "y_ratio"

/**
 * Movable iPhone AssistiveTouch-style floating refresh button overlay.
 * - Tracks true root coordinates via onGloballyPositioned for jitter-free 1:1 dragging.
 * - Tap triggers immediate refresh; drag repositions without triggering refresh.
 * - Magnetizes to the nearest left or right screen edge on release across all screen sizes.
 * - Persists docked side and normalized vertical position across rotations and restarts.
 */
@Composable
fun FloatingRefreshOverlay(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) {
        context.getSharedPreferences(REFRESH_PREFS, Context.MODE_PRIVATE)
    }

    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentIsRefreshing by rememberUpdatedState(isRefreshing)

    val buttonSizeDp = 50.dp
    val edgeMarginDp = 12.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        val buttonSizePx = with(density) { buttonSizeDp.toPx() }
        val marginPx = with(density) { edgeMarginDp.toPx() }
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()
        val minX = marginPx
        val minY = marginPx
        val maxX = (maxWidthPx - buttonSizePx - marginPx).coerceAtLeast(minX)
        val maxY = (maxHeightPx - buttonSizePx - marginPx).coerceAtLeast(minY)
        val spanY = (maxY - minY).coerceAtLeast(1f)

        var initialized by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        var placedInRoot by remember { mutableStateOf(Offset.Zero) }
        val animX = remember { Animatable(maxX) }
        val animY = remember { Animatable(minY + spanY * 0.88f) }

        // Restore saved side-docked coordinates when bounds become known or change (e.g. rotation)
        LaunchedEffect(maxX, maxY) {
            val savedXRatio = prefs.getFloat(KEY_X_RATIO, 1f).coerceIn(0f, 1f)
            val savedYRatio = prefs.getFloat(KEY_Y_RATIO, 0.88f).coerceIn(0f, 1f)
            val targetX = if (savedXRatio <= 0.5f) minX else maxX
            val targetY = (minY + savedYRatio * spanY).coerceIn(minY, maxY)
            if (!initialized) {
                animX.snapTo(targetX)
                animY.snapTo(targetY)
                initialized = true
            } else if (!isDragging) {
                animX.snapTo(targetX)
                animY.snapTo(targetY)
            }
        }

        val spinTransition = rememberInfiniteTransition(label = "floatingRefreshSpin")
        val spinAngle by spinTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "floatingRefreshAngle"
        )

        val alpha = if (isDragging || isRefreshing) 0.96f else 0.78f
        val borderColor = if (isRefreshing) KsGreenPrimary else KsBorderStrong
        val iconTint = if (isRefreshing) KsGreenText else KsTextPrimary

        Surface(
            modifier = Modifier
                .offset { IntOffset(animX.value.roundToInt(), animY.value.roundToInt()) }
                .onGloballyPositioned { coords ->
                    placedInRoot = coords.positionInRoot()
                }
                .size(buttonSizeDp)
                .clip(CircleShape)
                .semantics {
                    contentDescription = "Refresh Orders"
                    role = Role.Button
                    onClick {
                        if (!currentIsRefreshing) currentOnRefresh()
                        true
                    }
                }
                .pointerInput(minX, maxX, minY, maxY) {
                    val touchSlop = viewConfig.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        val startRootX = placedInRoot.x + down.position.x
                        val startRootY = placedInRoot.y + down.position.y
                        val startAnimX = animX.value
                        val startAnimY = animY.value
                        var dragged = false
                        var cancelled = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break

                            if (!change.pressed) {
                                change.consume()
                                break
                            }

                            val pointerRootX = placedInRoot.x + change.position.x
                            val pointerRootY = placedInRoot.y + change.position.y
                            val dx = pointerRootX - startRootX
                            val dy = pointerRootY - startRootY

                            if (!dragged && hypot(dx, dy) >= touchSlop) {
                                dragged = true
                                isDragging = true
                            }

                            if (dragged) {
                                change.consume()
                                val nextX = (startAnimX + dx).coerceIn(minX, maxX)
                                val nextY = (startAnimY + dy).coerceIn(minY, maxY)
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    animX.snapTo(nextX)
                                    animY.snapTo(nextY)
                                }
                            } else if (change.isConsumed) {
                                cancelled = true
                                break
                            }
                        }

                        isDragging = false

                        if (!dragged && !cancelled) {
                            if (!currentIsRefreshing) currentOnRefresh()
                        } else if (dragged) {
                            val currentX = animX.value.coerceIn(minX, maxX)
                            val currentY = animY.value.coerceIn(minY, maxY)
                            val midX = (minX + maxX) / 2f
                            val isLeftSide = currentX <= midX
                            val targetX = if (isLeftSide) minX else maxX
                            val xRatio = if (isLeftSide) 0f else 1f
                            val yRatio = ((currentY - minY) / spanY).coerceIn(0f, 1f)
                            prefs.edit()
                                .putFloat(KEY_X_RATIO, xRatio)
                                .putFloat(KEY_Y_RATIO, yRatio)
                                .apply()

                            scope.launch {
                                animX.animateTo(
                                    targetValue = targetX,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    }
                },
            shape = CircleShape,
            color = KsSurfaceElevated.copy(alpha = alpha),
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(if (isRefreshing) spinAngle else 0f)
                )
            }
        }
    }
}
