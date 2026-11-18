package com.kitchensync360.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitchensync360.app.model.OrderWithItems
import com.kitchensync360.app.model.parseIsoMillis
import com.kitchensync360.app.ui.theme.KsBackground
import com.kitchensync360.app.ui.theme.KsBlueText
import com.kitchensync360.app.ui.theme.KsBorder
import com.kitchensync360.app.ui.theme.KsBorderStrong
import com.kitchensync360.app.ui.theme.KsGreenPrimary
import com.kitchensync360.app.ui.theme.KsRedDanger
import com.kitchensync360.app.ui.theme.KsRedSurface
import com.kitchensync360.app.ui.theme.KsRedText
import com.kitchensync360.app.ui.theme.KsSurface
import com.kitchensync360.app.ui.theme.KsSurfaceElevated
import com.kitchensync360.app.ui.theme.KsTextMuted
import com.kitchensync360.app.ui.theme.KsTextPrimary
import com.kitchensync360.app.ui.theme.KsTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Plain surface card with 1dp border and optional 4dp left status stripe for instant scanning.
 */
@Composable
fun PlainCard(
    modifier: Modifier = Modifier,
    borderColor: Color = KsBorder,
    backgroundColor: Color = KsSurface,
    accentStripeColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        if (accentStripeColor != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accentStripeColor)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(contentPadding),
                    content = content
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * Plain modal dialog:
 * - No horizontal divider lines separating header/body/footer (uses clean spacing instead).
 * - Any header icon has NO background container; body content has ZERO left indentation.
 */
@Composable
fun PlainModalDialog(
    title: String,
    subtitle: String? = null,
    headerIcon: ImageVector? = null,
    maxWidth: Dp = 600.dp,
    dismissEnabled: Boolean = true,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            shape = RoundedCornerShape(8.dp),
            color = KsSurface,
            border = BorderStroke(1.dp, KsBorderStrong)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (headerIcon != null) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = null,
                                tint = KsTextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = KsTextPrimary
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = if (dismissEnabled) KsTextSecondary else KsTextMuted,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(enabled = dismissEnabled, onClick = onDismiss)
                    )
                }

                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = KsTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content
                )
            }
        }
    }
}

/**
 * Reusable structured confirmation modal matching web-dashboard ConfirmModal.
 * Uses clean tonal cards without internal divider lines and equal-width footer buttons.
 */
@Composable
fun PlainConfirmModal(
    title: String,
    subtitle: String? = null,
    confirmLabel: String,
    cancelLabel: String = "Cancel",
    destructive: Boolean = false,
    busy: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    PlainModalDialog(
        title = title,
        subtitle = subtitle,
        maxWidth = 490.dp,
        dismissEnabled = !busy,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KsRedText
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlainOutlineButton(
                    text = cancelLabel,
                    onClick = onDismiss,
                    enabled = !busy,
                    minHeight = 42.dp,
                    modifier = Modifier.weight(1f)
                )
                PlainPrimaryButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    enabled = !busy,
                    containerColor = if (destructive) KsRedDanger else KsGreenPrimary,
                    minHeight = 42.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Tonal summary container for confirmation modals (no outer border or inner line dividers).
 */
@Composable
fun ConfirmSummaryCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(KsBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun ConfirmMetaCell(
    label: String,
    value: String,
    valueColor: Color = KsTextPrimary,
    subValue: String? = null,
    subColor: Color = KsTextSecondary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KsTextMuted,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subValue.isNullOrBlank()) {
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelMedium,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ConfirmNoticeBlock(
    title: String? = null,
    body: String,
    isDanger: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isDanger) KsRedSurface else KsSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isDanger) KsRedText else KsTextPrimary
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDanger) KsTextPrimary else KsTextSecondary
        )
    }
}

@Composable
fun StatusCountPill(
    count: Int,
    textColor: Color,
    backgroundColor: Color,
    borderColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .then(
                if (borderColor != null) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun PlainPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = KsGreenPrimary,
    minHeight: Dp = 40.dp
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = KsSurfaceElevated,
            disabledContentColor = KsTextMuted
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PlainOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = KsBorderStrong,
    textColor: Color = KsTextPrimary,
    minHeight: Dp = 40.dp
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (enabled) borderColor else KsBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = textColor,
            disabledContentColor = KsTextMuted
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatusDot(
    color: Color,
    size: Dp = 7.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun PlainBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isError) KsRedSurface else KsSurfaceElevated
    val borderColor = if (isError) KsRedDanger else KsBorderStrong
    val textColor = if (isError) KsRedText else KsTextPrimary
    val dotColor = if (isError) KsRedText else KsBlueText

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                StatusDot(color = dotColor, size = 7.dp)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Dismiss",
                tint = KsTextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onDismiss)
            )
        }
    }
}

/**
 * Single-row user-friendly overdue alert strip matching web-dashboard:
 * status dot + concise label + horizontally scrollable table chips + optional right quick action.
 */
@Composable
fun OverdueAlertBanner(
    overdueOrders: List<OrderWithItems>,
    nowEpochMillis: Long,
    compactLabel: String = "Waiting",
    expandedLabel: String = "Waiting for Waiter",
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (overdueOrders.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(KsRedSurface)
            .border(1.dp, KsRedDanger.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        val labelText = if (maxWidth >= 560.dp) expandedLabel else compactLabel

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusDot(color = KsRedText, size = 7.dp)
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = KsRedText,
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                overdueOrders.forEach { orderWithItems ->
                    val waiting = formatElapsedShort(orderWithItems.readyElapsedSeconds(nowEpochMillis))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(KsSurface.copy(alpha = 0.85f))
                            .border(1.dp, KsRedDanger.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                            .then(
                                if (onActionClick != null) Modifier.clickable(onClick = onActionClick) else Modifier
                            )
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Table ${orderWithItems.order.tableNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = KsTextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "· ${orderWithItems.displayOrderNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            color = KsTextSecondary,
                            maxLines = 1
                        )
                        Text(
                            text = waiting,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = KsRedText,
                            maxLines = 1
                        )
                    }
                }
            }

            if (actionLabel != null && onActionClick != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(KsSurface.copy(alpha = 0.72f))
                        .border(1.dp, KsRedDanger.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                        .clickable(onClick = onActionClick)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = KsRedText,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Shared responsive multi-column grid emitter for LazyColumn:
 * 1 column on phones (< 680dp), 2 columns on tablets (680-1049dp), 3 columns on wide displays (>= 1050dp).
 * Each row uses IntrinsicSize.Min so cards stretch to match the tallest card in the row.
 */
fun <T> LazyListScope.responsiveCardRows(
    itemsList: List<T>,
    columnCount: Int,
    keyPrefix: String,
    itemKey: (T) -> String,
    itemContent: @Composable (T) -> Unit
) {
    if (columnCount <= 1) {
        items(itemsList.size, key = { idx -> "${keyPrefix}_${itemKey(itemsList[idx])}" }) { idx ->
            itemContent(itemsList[idx])
        }
    } else {
        val rows = itemsList.chunked(columnCount)
        items(rows.size, key = { idx -> "${keyPrefix}_row_${itemKey(rows[idx].first())}" }) { idx ->
            val rowItems = rows[idx]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (col in 0 until columnCount) {
                    if (col < rowItems.size) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            itemContent(rowItems[col])
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

fun responsiveColumnCount(availableWidth: Dp): Int = when {
    availableWidth >= 1050.dp -> 3
    availableWidth >= 680.dp -> 2
    else -> 1
}

fun formatElapsedShort(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    return when {
        s < 60L -> "${s}s"
        s < 3600L -> {
            val mins = s / 60L
            val rem = s % 60L
            if (rem == 0L) "${mins}m" else "${mins}m ${rem}s"
        }
        else -> "${s / 3600L}h ${(s % 3600L) / 60L}m"
    }
}

private val CLOCK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

fun formatClockShort(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return runCatching {
        val millis = parseIsoMillis(isoString)
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(CLOCK_FORMATTER)
    }.getOrDefault("")
}
