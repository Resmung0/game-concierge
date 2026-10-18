package com.example.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.ChatUiState
import com.example.chat.ui.ConversationView
import com.example.context.data.DefaultGameContextRepository
import com.example.context.data.GameContextRepository
import com.example.context.model.GameSessionContext
import com.example.context.ui.GameContextPanel
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.GameBorder
import com.example.ui.theme.GameDarkCard
import com.example.ui.theme.GameDarkSurface
import com.example.ui.theme.GameDarkSurfaceVariant
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private val POPULAR_GAMES = listOf(
    "Elden Ring",
    "Zelda: TotK",
    "Hollow Knight",
    "Cyberpunk 2077"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpandedPanel(
    activeGame: String,
    chatUiState: ChatUiState,
    sessionContext: GameSessionContext,
    contextRepo: GameContextRepository,
    onSendMessage: (String) -> Unit,
    onCancelChat: () -> Unit,
    onRetryChat: () -> Unit,
    onClearChat: () -> Unit,
    onCaptureScreenshot: () -> Unit = {},
    onSelectGame: (String) -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeModeIndex by remember { mutableIntStateOf(0) } // 0 -> Chat, 1 -> Context & Guides
    val panelShape = RoundedCornerShape(20.dp)

    Surface(
        modifier = modifier
            .testTag("expanded_panel")
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = panelShape)
            .clip(panelShape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(CyberCyan.copy(alpha = 0.7f), NeonPurple.copy(alpha = 0.4f))
                ),
                shape = panelShape
            ),
        color = GameDarkSurface.copy(alpha = 0.98f),
        shape = panelShape
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Title, Status Badge, Minimize, Clear & Close Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Brush.linearGradient(listOf(CyberCyan, NeonPurple)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gamepad,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "GAME CONCIERGE",
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            letterSpacing = 1.2.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(NeonEmerald, CircleShape)
                            )
                            Text(
                                text = "Context & Voice • Phase 6",
                                color = NeonEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeModeIndex == 0) {
                        // Clear history button (active in chat mode)
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier
                                .testTag("btn_clear_chat")
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear chat history",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Minimize to capsule
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .testTag("btn_collapse_overlay")
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Collapse overlay to capsule",
                            tint = TextSecondary
                        )
                    }

                    // Close overlay completely
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .testTag("btn_close_overlay")
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close overlay",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Game Selector Row
            Text(
                text = "ACTIVE GAME CONTEXT",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                POPULAR_GAMES.forEach { game ->
                    val isSelected = activeGame.equals(game, ignoreCase = true)
                    val gameId = game.lowercase().replace(":", "").replace(" ", "_")
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onSelectGame(game)
                            contextRepo.selectGame(gameId, game)
                        },
                        label = { Text(game, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan,
                            containerColor = GameDarkCard,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) CyberCyan else GameBorder
                        ),
                        modifier = Modifier.testTag("chip_game_$game")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Navigation: Chat vs Context & Guides
            SecondaryTabRow(
                selectedTabIndex = activeModeIndex,
                containerColor = GameDarkSurfaceVariant,
                contentColor = CyberCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = activeModeIndex == 0,
                    onClick = { activeModeIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Chat & Guidance", fontSize = 12.sp, fontWeight = if (activeModeIndex == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    },
                    modifier = Modifier.testTag("tab_mode_chat")
                )

                Tab(
                    selected = activeModeIndex == 1,
                    onClick = { activeModeIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Context & Guides", fontSize = 12.sp, fontWeight = if (activeModeIndex == 1) FontWeight.Bold else FontWeight.Normal)
                            if (sessionContext.pendingConfirmations.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = CyberAmber,
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = sessionContext.pendingConfirmations.size.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("tab_mode_context")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeModeIndex == 0) {
                // Mode 0: Conversational Chat
                ConversationView(
                    uiState = chatUiState,
                    activeGame = activeGame,
                    onSendMessage = onSendMessage,
                    onCancel = onCancelChat,
                    onRetry = onRetryChat,
                    onClear = onClearChat,
                    onCaptureScreenshot = onCaptureScreenshot
                )
            } else {
                // Mode 1: Context Engine & Guides
                GameContextPanel(
                    sessionContext = sessionContext,
                    contextRepo = contextRepo
                )
            }
        }
    }
}
