package com.kitchensync360.app.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchensync360.app.data.RepositorySnapshot
import com.kitchensync360.app.model.AppRole
import com.kitchensync360.app.model.ConnectionMode
import com.kitchensync360.app.model.OrderStatus
import com.kitchensync360.app.model.OrderWithItems
import com.kitchensync360.app.model.SyncStatus
import com.kitchensync360.app.ui.theme.KsAmberText
import com.kitchensync360.app.ui.theme.KsBackground
import com.kitchensync360.app.ui.theme.KsBlueInfo
import com.kitchensync360.app.ui.theme.KsBlueText
import com.kitchensync360.app.ui.theme.KsGreenPrimary
import com.kitchensync360.app.ui.theme.KsGreenText
import com.kitchensync360.app.ui.theme.KsRedDanger
import com.kitchensync360.app.ui.theme.KsRedText
import com.kitchensync360.app.ui.theme.KsTextMuted
import com.kitchensync360.app.ui.theme.KsTextPrimary
import com.kitchensync360.app.ui.theme.KsTextSecondary
import kotlin.math.asin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * True on Wear OS, or on any screen too small for the phone layout (smallest width under 260dp).
 */
fun isWatchLayout(context: Context, configuration: Configuration): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) ||
        configuration.smallestScreenWidthDp in 1 until 260

/** Accent pair from the app palette: [solid] for rings, glows and buttons; [text] for labels. */
private data class Accent(val solid: Color, val text: Color)

private val GreenAccent = Accent(KsGreenPrimary, KsGreenText)
private val RedAccent = Accent(KsRedDanger, KsRedText)
private val BlueAccent = Accent(KsBlueInfo, KsBlueText)
private val NeutralAccent = Accent(Color(0xFF4B5F58), KsTextSecondary)

/** Fraction of the circle kept above the flat cut of the dome. */
private const val DOME_KEEP = 0.8f

/**
 * A circle with its bottom sliced off flat: the arc runs over the top and a straight
 * chord closes the bottom, so the shape sits on the status label below it.
 */
private val DomeShape = GenericShape { size, _ ->
    val diameter = size.width
    val radius = diameter / 2f
    // The cut is at the bottom of this shape's bounds; find where the chord meets the circle.
    val dy = (size.height - radius).coerceIn(-radius, radius)
    val cutAngle = Math.toDegrees(asin((dy / radius).toDouble())).toFloat()
    arcTo(
        rect = Rect(0f, 0f, diameter, diameter),
        startAngleDegrees = cutAngle,
        sweepAngleDegrees = -(180f + 2f * cutAngle),
        forceMoveTo = true
    )
    close()
}

/**
 * Glanceable waiter view for a watch, built around the circle:
 *  - a ring around the screen edge in the status color (pulses when overdue),
 *  - a frosted-glass dome with the table number,
 *  - the status label under the dome's flat edge,
 *  - one action button below, and a "1 of 3" counter above.
 * Every page uses the same slots, so nothing shifts when swiping between orders.
 */
@Composable
fun WatchScreen(
    snapshot: RepositorySnapshot,
    isRound: Boolean,
    /** Null in release builds: there is no one-tap demo sign-in, so the watch asks to sign in on the phone. */
    onSignInAsWaiter: (() -> Unit)?,
    onAcknowledgeOrder: (orderId: String) -> Unit,
    onMarkPickedUp: (orderId: String) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onDismissBanner: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(KsBackground)
    ) {
        val side = minOf(maxWidth, maxHeight)
        // Sized from a 227dp reference watch; system font scaling is cancelled so the layout always fits.
        val s = (side.value / 227f).coerceIn(0.8f, 1.4f)
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val scale = WatchScale(s = s, fontScale = fontScale, side = side, isRound = isRound)
        val user = snapshot.currentUser
        val connection = connectionWarning(snapshot)
        val banner = snapshot.bannerMessage?.takeIf { it.isNotBlank() && snapshot.bannerIsError }

        when {
            user == null -> WatchPage(scale, GreenAccent, topLabel = connection?.first, topLabelColor = connection?.second) {
                Dome(scale, GreenAccent) { DomeHeadline("Waiter", scale) }
                StatusLabel(
                    text = banner ?: if (onSignInAsWaiter != null) "Sign In for Alerts" else "Sign In on Phone",
                    color = if (banner != null) KsRedText else KsTextSecondary,
                    scale = scale,
                    onClick = if (banner != null) onDismissBanner else null
                )
                ActionSlot(scale) {
                    if (onSignInAsWaiter != null) {
                        PillButton("Sign In", GreenAccent, scale.buttonWidth, scale, onClick = onSignInAsWaiter)
                    }
                }
            }

            user.appRole != AppRole.WAITER -> WatchPage(scale, NeutralAccent, topLabel = null) {
                Dome(scale, NeutralAccent) { DomeHeadline("Waiters Only", scale) }
                StatusLabel("Use the Waiter Account", KsTextSecondary, scale)
                ActionSlot(scale) {
                    if (onSignInAsWaiter != null) {
                        PillButton("Switch", GreenAccent, scale.buttonWidth, scale, onClick = onSignInAsWaiter)
                    } else {
                        PillButton("Sign Out", NeutralAccent, scale.buttonWidth, scale, subtle = true, onClick = onSignOut)
                    }
                }
            }

            else -> {
                val queue = snapshot.orders
                    .filter {
                        (it.order.orderStatus == OrderStatus.READY || it.order.orderStatus == OrderStatus.ACKNOWLEDGED) &&
                            (it.order.assignedWaiterId == null || it.order.assignedWaiterId == user.id)
                    }
                    .sortedWith(
                        compareBy<OrderWithItems> { if (it.order.orderStatus == OrderStatus.READY) 0 else 1 }
                            .thenBy { it.order.readyAtMillis ?: Long.MAX_VALUE }
                    )

                // Pages: each ready order (or one "All clear" page), then a single Settings page.
                val empty = queue.isEmpty()
                val contentPages = if (empty) 1 else queue.size
                val settingsPage = contentPages
                val pagerState = rememberPagerState(pageCount = { contentPages + 1 })
                val pagerScope = rememberCoroutineScope()
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when {
                        page == settingsPage -> SettingsPage(
                            scale = scale,
                            snapshot = snapshot,
                            connection = connection,
                            banner = banner,
                            onDismissBanner = onDismissBanner,
                            onRefresh = onRefresh,
                            onSignOut = onSignOut
                        )

                        empty -> AllClearPage(
                            scale = scale,
                            connection = connection,
                            banner = banner,
                            onDismissBanner = onDismissBanner,
                            onOpenSettings = { pagerScope.launch { pagerState.animateScrollToPage(settingsPage) } }
                        )

                        else -> {
                            val order = queue[page]
                            OrderPage(
                                scale = scale,
                                order = order,
                                position = "${page + 1} of ${queue.size}",
                                now = now,
                                isPending = order.order.id in snapshot.pendingOrderIds,
                                failure = snapshot.failedWriteByOrderId[order.order.id],
                                connection = connection,
                                onAcknowledge = { onAcknowledgeOrder(order.order.id) },
                                onPickedUp = { onMarkPickedUp(order.order.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private class WatchScale(val s: Float, private val fontScale: Float, val side: Dp, val isRound: Boolean) {
    fun dp(value: Float): Dp = (value * s).dp
    fun sp(value: Float): TextUnit = (value * s / fontScale).sp

    val domeWidth: Dp = minOf(dp(118f), side * 0.53f)
    val domeHeight: Dp = domeWidth * DOME_KEEP
    val buttonWidth: Dp = side * 0.46f
    val buttonHeight: Dp = dp(34f)
}

/** Shared frame: edge ring + centered slots (counter, dome, status label, action). */
@Composable
private fun WatchPage(
    scale: WatchScale,
    accent: Accent,
    topLabel: String?,
    topLabelColor: Color? = null,
    pulse: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        EdgeRing(scale, accent, pulse)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.height(scale.dp(15f)), contentAlignment = Alignment.Center) {
                if (topLabel != null) {
                    Text(
                        text = topLabel,
                        color = topLabelColor ?: KsTextMuted,
                        fontSize = scale.sp(11f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(scale.dp(6f)))
            content()
        }
    }
}

@Composable
private fun EdgeRing(scale: WatchScale, accent: Accent, pulse: Boolean) {
    val alpha = if (pulse) {
        val transition = rememberInfiniteTransition(label = "ring")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "ringAlpha"
        ).value
    } else {
        0.85f
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 3.dp.toPx() * scale.s
        val inset = 3.dp.toPx() * scale.s
        val ringBrush = Brush.sweepGradient(
            listOf(
                accent.solid.copy(alpha = alpha),
                accent.solid.copy(alpha = alpha * 0.35f),
                accent.solid.copy(alpha = alpha),
                accent.solid.copy(alpha = alpha * 0.35f),
                accent.solid.copy(alpha = alpha)
            )
        )
        if (scale.isRound) {
            drawCircle(
                brush = ringBrush,
                radius = size.minDimension / 2f - inset - stroke / 2f,
                style = Stroke(width = stroke)
            )
        } else {
            drawRoundRect(
                brush = ringBrush,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(24.dp.toPx()),
                style = Stroke(width = stroke)
            )
        }
    }
}

/** Frosted-glass dome (circle with a flat bottom) with a soft glow of the accent color behind it. */
@Composable
private fun Dome(scale: WatchScale, accent: Accent, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .width(scale.domeWidth)
            .height(scale.domeHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .requiredSize(scale.domeWidth * 1.75f)
                .background(
                    Brush.radialGradient(
                        0f to accent.solid.copy(alpha = 0.40f),
                        0.45f to accent.solid.copy(alpha = 0.12f),
                        1f to Color.Transparent
                    )
                )
        )
        Column(
            modifier = Modifier
                .width(scale.domeWidth)
                .height(scale.domeHeight)
                .clip(DomeShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))
                    )
                )
                .background(accent.solid.copy(alpha = 0.08f))
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.10f)))
                    ),
                    DomeShape
                )
                // Content sits slightly low: the dome is widest near its flat base.
                .padding(top = scale.dp(10f)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** The label under the dome's flat edge, e.g. "GO COLLECT". [blink] fades it in and out; faster when [urgent]. */
@Composable
private fun StatusLabel(
    text: String,
    color: Color,
    scale: WatchScale,
    blink: Boolean = false,
    urgent: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val alpha = if (blink) {
        val transition = rememberInfiniteTransition(label = "statusBlink")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(if (urgent) 450 else 900), RepeatMode.Reverse),
            label = "statusAlpha"
        ).value
    } else {
        1f
    }
    Spacer(modifier = Modifier.height(scale.dp(7f)))
    Box(
        modifier = Modifier
            .height(scale.dp(17f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = color.copy(alpha = color.alpha * alpha),
            fontSize = scale.sp(12f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = scale.sp(2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActionSlot(scale: WatchScale, content: @Composable () -> Unit) {
    Spacer(modifier = Modifier.height(scale.dp(8f)))
    Box(modifier = Modifier.height(scale.buttonHeight), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun OrderPage(
    scale: WatchScale,
    order: OrderWithItems,
    position: String,
    now: Long,
    isPending: Boolean,
    failure: String?,
    connection: Pair<String, Color>?,
    onAcknowledge: () -> Unit,
    onPickedUp: () -> Unit
) {
    val isReady = order.order.orderStatus == OrderStatus.READY
    val overdue = order.isUnacknowledgedOverdue(now)
    val accent = when {
        overdue -> RedAccent
        isReady -> GreenAccent
        else -> BlueAccent
    }
    val status = when {
        overdue -> "Waiting ${formatElapsed(order.readyElapsedSeconds(now))}"
        isReady -> "Food Ready"
        else -> "Go Collect"
    }
    val (topLabel, topColor) = when {
        !failure.isNullOrBlank() -> "Not Saved · Try Again" to KsRedText
        connection != null -> connection
        else -> "$position · ${order.displayOrderNumber}" to KsTextMuted
    }

    WatchPage(scale = scale, accent = accent, topLabel = topLabel, topLabelColor = topColor, pulse = overdue) {
        Dome(scale, accent) {
            Text(
                text = "TABLE",
                color = KsTextSecondary,
                fontSize = scale.sp(9.5f),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = scale.sp(2.5f)
            )
            Text(
                text = order.order.tableNumber.toString(),
                color = KsTextPrimary,
                fontSize = scale.sp(42f),
                lineHeight = scale.sp(44f),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        StatusLabel(text = status, color = accent.text, scale = scale, blink = true, urgent = overdue)
        ActionSlot(scale) {
            PillButton(
                text = when {
                    isPending -> "Sending…"
                    isReady -> "Got It"
                    else -> "Picked Up"
                },
                accent = if (isReady) GreenAccent else BlueAccent,
                width = scale.buttonWidth,
                scale = scale,
                enabled = !isPending,
                onClick = if (isReady) onAcknowledge else onPickedUp
            )
        }
    }
}

@Composable
private fun DomeHeadline(text: String, scale: WatchScale) {
    Text(
        text = text,
        color = KsTextPrimary,
        fontSize = scale.sp(17f),
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}


/** Glossy pill: accent glass with a light top edge. [subtle] is clear glass for secondary actions. */
@Composable
private fun PillButton(
    text: String,
    accent: Accent,
    width: Dp,
    scale: WatchScale,
    subtle: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    val contentColor = if (subtle) KsTextSecondary else Color.White
    val fill = if (subtle) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.05f)))
    } else {
        Brush.verticalGradient(listOf(lerp(accent.solid, Color.White, 0.2f), accent.solid))
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(scale.buttonHeight)
            .clip(shape)
            .background(fill, alpha = if (enabled) 1f else 0.45f)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (subtle) 0.3f else 0.45f), Color.White.copy(alpha = 0.05f)))
                ),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(scale.dp(15f))
                )
                Spacer(modifier = Modifier.width(scale.dp(5f)))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = if (subtle) scale.sp(12f) else scale.sp(14f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Idle screen. Deliberately quiet: no ring, glow or dome (those mean "an order needs you"),
 * just a check, a short headline, one line of context and a small settings button at the bottom.
 */
@Composable
private fun AllClearPage(
    scale: WatchScale,
    connection: Pair<String, Color>?,
    banner: String?,
    onDismissBanner: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val (subtitle, subtitleColor) = when {
        banner != null -> banner to KsRedText
        connection != null -> connection
        else -> "No Orders Waiting" to KsTextSecondary
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = scale.dp(28f)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = KsGreenText,
                modifier = Modifier.size(scale.dp(30f))
            )
            Spacer(modifier = Modifier.height(scale.dp(6f)))
            Text(
                text = "All Clear",
                color = KsTextPrimary,
                fontSize = scale.sp(20f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(scale.dp(3f)))
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = scale.sp(12f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (banner != null) Modifier.clickable(onClick = onDismissBanner) else Modifier
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = scale.dp(16f))
                .size(scale.dp(36f))
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = KsTextSecondary,
                modifier = Modifier.size(scale.dp(18f))
            )
        }
    }
}

/**
 * Settings: deliberately not a dome, so it never looks like an order. A small heading,
 * a connection chip, who is signed in, then two large round buttons with labels.
 */
@Composable
private fun SettingsPage(
    scale: WatchScale,
    snapshot: RepositorySnapshot,
    connection: Pair<String, Color>?,
    banner: String?,
    onDismissBanner: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    val syncing = snapshot.isManualRefreshing
    // Nothing under the name while all is well; a warning line appears only when something needs attention.
    val warning: Pair<String, Color>? = when {
        banner != null -> banner to KsRedText
        else -> connection
    }
    Box(modifier = Modifier.fillMaxSize()) {
        EdgeRing(scale, NeutralAccent, pulse = false)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = scale.dp(26f)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SETTINGS",
                color = KsTextMuted,
                fontSize = scale.sp(10f),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = scale.sp(2.5f)
            )
            Spacer(modifier = Modifier.height(scale.dp(10f)))
            Text(
                text = snapshot.currentUser?.displayName ?: "Waiter",
                color = KsTextPrimary,
                fontSize = scale.sp(15f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (warning != null) {
                Box(
                    modifier = Modifier
                        .height(scale.dp(15f))
                        .then(if (banner != null) Modifier.clickable(onClick = onDismissBanner) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = warning.first,
                        color = warning.second,
                        fontSize = scale.sp(10f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(scale.dp(14f)))
            Row(horizontalArrangement = Arrangement.spacedBy(scale.dp(22f))) {
                RoundAction(
                    icon = Icons.Rounded.Refresh,
                    label = if (syncing) "Syncing" else "Sync",
                    scale = scale,
                    tint = KsTextPrimary,
                    edge = Color.White.copy(alpha = 0.35f),
                    spinning = syncing,
                    enabled = !syncing,
                    onClick = onRefresh
                )
                RoundAction(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    label = "Sign Out",
                    scale = scale,
                    tint = KsRedText,
                    edge = KsRedDanger.copy(alpha = 0.7f),
                    onClick = onSignOut
                )
            }
        }
    }
}

/** Round glass icon button with its label underneath. */
@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    scale: WatchScale,
    tint: Color,
    edge: Color,
    spinning: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val rotation = if (spinning) {
        val transition = rememberInfiniteTransition(label = "roundActionSpin")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Restart),
            label = "roundActionAngle"
        ).value
    } else {
        0f
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(scale.dp(48f))
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)))
                )
                .border(1.dp, edge, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier
                    .size(scale.dp(22f))
                    .rotate(rotation)
            )
        }
        Spacer(modifier = Modifier.height(scale.dp(4f)))
        Text(
            text = label,
            color = KsTextSecondary,
            fontSize = scale.sp(10.5f),
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Null while live. Otherwise a short warning and its color. */
private fun connectionWarning(snapshot: RepositorySnapshot): Pair<String, Color>? = when {
    snapshot.connectionMode != ConnectionMode.SUPABASE_CLOUD -> "Demo Mode" to KsAmberText
    snapshot.syncStatus == SyncStatus.CONNECTED -> null
    snapshot.syncStatus == SyncStatus.DISCONNECTED -> "Offline" to KsRedText
    else -> "Reconnecting…" to KsAmberText
}

private fun formatElapsed(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} hr"
}
