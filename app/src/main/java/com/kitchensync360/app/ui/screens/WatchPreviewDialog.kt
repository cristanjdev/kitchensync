package com.kitchensync360.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitchensync360.app.data.RepositorySnapshot
import com.kitchensync360.app.ui.components.PlainOutlineButton
import com.kitchensync360.app.ui.theme.KsTextSecondary
import com.kitchensync360.app.ui.theme.KsTextPrimary

/**
 * Demo builds only: shows the real Wear OS [WatchScreen] inside a round watch frame on the phone,
 * for demos where the client has no watch yet. It shares the phone's live data, so acknowledging or
 * picking up here does the same as on a real watch. Sign Out on the preview just closes it, so it
 * never signs the phone out.
 */
@Composable
fun WatchPreviewDialog(
    snapshot: RepositorySnapshot,
    onAcknowledgeOrder: (orderId: String) -> Unit,
    onMarkPickedUp: (orderId: String) -> Unit,
    onRefresh: () -> Unit,
    onDismissBanner: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050606))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            // Watch screen ~ the emulator's 227dp, shrunk only on very narrow phones.
            val screen = minOf(240.dp, maxWidth * 0.66f)
            val bezel = screen * 0.07f
            val caseSize = screen + bezel * 2

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Watch Preview", style = MaterialTheme.typography.titleLarge, color = KsTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Same Screen as the Wear OS App",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KsTextSecondary
                )
                Spacer(Modifier.height(20.dp))

                // Swallow taps on the watch so they don't close the preview.
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    // Strap.
                    Box(
                        Modifier
                            .width(screen * 0.62f)
                            .height(caseSize + screen * 0.5f)
                            .clip(RoundedCornerShape(screen * 0.12f))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF15191A), Color(0xFF242A2B), Color(0xFF15191A))
                                )
                            )
                    )
                    // Crown.
                    Box(
                        Modifier
                            .offset(x = caseSize / 2 + bezel * 0.2f)
                            .size(width = bezel * 0.9f, height = screen * 0.16f)
                            .clip(RoundedCornerShape(bezel * 0.3f))
                            .background(Color(0xFF3A4143))
                    )
                    // Case and bezel.
                    Box(
                        modifier = Modifier
                            .size(caseSize)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF4A5254), Color(0xFF1B1F20)))
                            )
                            .padding(bezel * 0.18f)
                            .clip(CircleShape)
                            .background(Color(0xFF0B0D0E))
                            .padding(bezel * 0.82f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(screen)
                                .clip(CircleShape)
                                .border(0.5.dp, Color.White.copy(alpha = 0.06f), CircleShape)
                        ) {
                            WatchScreen(
                                snapshot = snapshot,
                                isRound = true,
                                onSignInAsWaiter = null,
                                onAcknowledgeOrder = onAcknowledgeOrder,
                                onMarkPickedUp = onMarkPickedUp,
                                onRefresh = onRefresh,
                                onSignOut = onClose,
                                onDismissBanner = onDismissBanner
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "Swipe Left for More Orders and Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = KsTextSecondary
                )
                Spacer(Modifier.height(12.dp))
                PlainOutlineButton(text = "Close", onClick = onClose, modifier = Modifier.width(140.dp))
            }
        }
    }
}
