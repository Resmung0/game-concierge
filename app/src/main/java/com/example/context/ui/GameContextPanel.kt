package com.example.context.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.context.data.GameContextRepository
import com.example.context.data.GameKnowledgeRepository
import com.example.context.data.InMemoryGameKnowledgeRepository
import com.example.context.model.GameKnowledgeItem
import com.example.context.model.GameObservation
import com.example.context.model.GameSessionContext
import com.example.context.model.GuideCategory
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GameContextPanel(
    sessionContext: GameSessionContext,
    contextRepo: GameContextRepository,
    knowledgeRepo: GameKnowledgeRepository = remember { InMemoryGameKnowledgeRepository() },
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<GuideCategory?>(null) }

    // Dialog state for correcting hypothesis
    var editingObservation by remember { mutableStateOf<GameObservation?>(null) }
    var correctionText by remember { mutableStateOf("") }

    // Dialog state for adding confirmed fact
    var showAddFactDialog by remember { mutableStateOf(false) }
    var newFactText by remember { mutableStateOf("") }

    // Dialog state for editing location/objective
    var showEditLocationDialog by remember { mutableStateOf(false) }
    var editLocationText by remember { mutableStateOf(sessionContext.currentLocation) }
    var editObjectiveText by remember { mutableStateOf(sessionContext.currentObjective) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GameDarkSurface)
    ) {
        // Location & Objective Summary Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = GameDarkCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GameBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                        Text(
                            text = sessionContext.currentLocation,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.TrackChanges, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(14.dp))
                        Text(
                            text = sessionContext.currentObjective,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = {
                        editLocationText = sessionContext.currentLocation
                        editObjectiveText = sessionContext.currentObjective
                        showEditLocationDialog = true
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("btn_edit_location_objective")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit location and objective",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tabs: 0 -> Hypotheses & Inferences, 1 -> Confirmed Facts, 2 -> Knowledge Guides
        val tabs = listOf(
            "Hypotheses (${sessionContext.pendingConfirmations.size})",
            "Confirmed (${sessionContext.confirmedFacts.size})",
            "Guides & Intel"
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = GameDarkSurfaceVariant,
            contentColor = CyberCyan,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyberCyan
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) CyberCyan else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_context_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 260.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: Hypotheses & Pending Confirmations
                    if (sessionContext.pendingConfirmations.isEmpty() && sessionContext.hypotheses.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No unconfirmed hypotheses. All game state observations are verified.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sessionContext.hypotheses, key = { it.id }) { item ->
                                HypothesisCard(
                                    observation = item,
                                    onConfirm = {
                                        contextRepo.confirmHypothesis(item.id)
                                    },
                                    onEdit = {
                                        editingObservation = item
                                        correctionText = item.content
                                    },
                                    onDismiss = {
                                        contextRepo.dismissHypothesis(item.id)
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: Confirmed Facts & Player Progress
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "VERIFIED PLAYER PROGRESS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald,
                                letterSpacing = 0.8.sp
                            )
                            TextButton(
                                onClick = { showAddFactDialog = true },
                                modifier = Modifier
                                    .testTag("btn_add_confirmed_fact")
                                    .height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonEmerald)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Fact", fontSize = 11.sp, color = NeonEmerald)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sessionContext.confirmedFacts, key = { it.id }) { fact ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = GameDarkCard,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = fact.content,
                                            fontSize = 12.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 2: Searchable Game Knowledge Guides
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search bosses, puzzles, collectibles…", fontSize = 11.sp, color = TextSecondary) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("guide_search_input"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = GameDarkCard,
                                unfocusedContainerColor = GameDarkCard,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = GameBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val filteredGuides = knowledgeRepo.queryGuides(
                            gameId = sessionContext.gameId,
                            query = searchQuery,
                            category = selectedCategory
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filteredGuides, key = { it.id }) { guide ->
                                GuideItemCard(guide = guide)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Correct Hypothesis
    if (editingObservation != null) {
        AlertDialog(
            onDismissRequest = { editingObservation = null },
            title = { Text("Correct Assistant Hypothesis", fontSize = 16.sp, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Provide the accurate game state to confirm this fact:", fontSize = 12.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = correctionText,
                        onValueChange = { correctionText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_hypothesis_correction"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val obs = editingObservation ?: return@Button
                        contextRepo.confirmHypothesis(obs.id, correctionText.trim())
                        editingObservation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                    modifier = Modifier.testTag("btn_save_hypothesis_correction")
                ) {
                    Text("Save & Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingObservation = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = GameDarkCard
        )
    }

    // Dialog: Add Confirmed Fact
    if (showAddFactDialog) {
        AlertDialog(
            onDismissRequest = { showAddFactDialog = false },
            title = { Text("Add Confirmed Progress Fact", fontSize = 16.sp, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Record a confirmed milestone (e.g. 'Defeated Margit', 'Found Sacred Tear'):", fontSize = 12.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = newFactText,
                        onValueChange = { newFactText = it },
                        placeholder = { Text("Enter milestone…", color = TextSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_new_confirmed_fact"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFactText.isNotBlank()) {
                            contextRepo.addConfirmedFact(newFactText.trim())
                            newFactText = ""
                            showAddFactDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black),
                    modifier = Modifier.testTag("btn_confirm_add_fact")
                ) {
                    Text("Add Fact")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFactDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = GameDarkCard
        )
    }

    // Dialog: Edit Location & Objective
    if (showEditLocationDialog) {
        AlertDialog(
            onDismissRequest = { showEditLocationDialog = false },
            title = { Text("Update Current Location & Objective", fontSize = 16.sp, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editLocationText,
                        onValueChange = { editLocationText = it },
                        label = { Text("Current Location") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_location"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = editObjectiveText,
                        onValueChange = { editObjectiveText = it },
                        label = { Text("Current Objective") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_objective"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        contextRepo.setLocation(editLocationText)
                        contextRepo.setObjective(editObjectiveText)
                        showEditLocationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                    modifier = Modifier.testTag("btn_save_location_objective")
                ) {
                    Text("Update Context")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLocationDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = GameDarkCard
        )
    }
}

@Composable
fun HypothesisCard(
    observation: GameObservation,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GameDarkCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberAmber.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CyberAmber.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, CyberAmber)
                ) {
                    Text(
                        text = "AGENT HYPOTHESIS (UNVERIFIED)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberAmber,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss hypothesis", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }

            Text(
                text = observation.content,
                fontSize = 12.sp,
                color = TextPrimary,
                lineHeight = 16.sp
            )

            // Confirmation & Edit Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("btn_edit_hypothesis_${observation.id}"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = CyberCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Correct", fontSize = 11.sp, color = CyberCyan)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("btn_confirm_hypothesis_${observation.id}"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Confirm", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GuideItemCard(guide: GameKnowledgeItem) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = GameDarkCard,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, GameBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = guide.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = NeonPurple.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = guide.category.name,
                        fontSize = 9.sp,
                        color = NeonPurple,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = guide.summary,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = GameBorder)
                    Text("Tactical Steps:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    guide.tacticalSteps.forEachIndexed { i, step ->
                        Text("${i + 1}. $step", fontSize = 11.sp, color = TextPrimary, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}
