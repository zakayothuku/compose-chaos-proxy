package io.github.zakayothuku.chaosproxy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zakayothuku.chaosproxy.engine.ChaosRule
import io.github.zakayothuku.chaosproxy.model.ChaosActionType
import io.github.zakayothuku.chaosproxy.model.ChaosEvent
import io.github.zakayothuku.chaosproxy.repository.ChaosConfigRepository
import io.github.zakayothuku.chaosproxy.repository.ChaosConfigState
import io.github.zakayothuku.chaosproxy.repository.ChaosPresetType

/**
 * Stateful Container collecting ChaosConfigRepository state.
 */
@Composable
fun ComposeChaosOverlay(
    modifier: Modifier = Modifier
) {
    val state by ChaosConfigRepository.state.collectAsState()

    ComposeChaosOverlayContent(
        state = state,
        onGlobalToggle = { ChaosConfigRepository.setGlobalEnabled(it) },
        onApplyPreset = { ChaosConfigRepository.applyPreset(it) },
        onAddRule = { ChaosConfigRepository.addRule(it) },
        onToggleRule = { ChaosConfigRepository.toggleRule(it) },
        onDeleteRule = { ChaosConfigRepository.deleteRule(it) },
        onClearEvents = { ChaosConfigRepository.clearEvents() },
        modifier = modifier
    )
}

/**
 * Stateless Content Composable adhering to Safaricom Compose Previews & Clean Arch standards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeChaosOverlayContent(
    state: ChaosConfigState,
    onGlobalToggle: (Boolean) -> Unit,
    onApplyPreset: (ChaosPresetType) -> Unit,
    onAddRule: (ChaosRule) -> Unit,
    onToggleRule: (String) -> Unit,
    onDeleteRule: (String) -> Unit,
    onClearEvents: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showRuleEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ChaosRule?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Rules, 1 = Live Events

    val activeRuleCount = remember(state.rules, state.globalEnabled) {
        if (!state.globalEnabled) 0 else state.rules.count { it.enabled }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Floating Chaos Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .clip(CircleShape)
                .background(
                    if (state.globalEnabled) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { isExpanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🌪️ Chaos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (state.globalEnabled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = CircleShape,
                    color = if (state.globalEnabled) MaterialTheme.colorScheme.error else Color.Gray
                ) {
                    Text(
                        text = "$activeRuleCount",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Full Screen Inspector & Controller Sheet
        if (isExpanded) {
            ModalBottomSheet(
                onDismissRequest = { isExpanded = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header with Master Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Network Chaos Proxy",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (state.globalEnabled) "Chaos Active ($activeRuleCount Rules)" else "Chaos Disabled",
                                fontSize = 12.sp,
                                color = if (state.globalEnabled) MaterialTheme.colorScheme.error else Color.Gray
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = state.globalEnabled,
                                onCheckedChange = onGlobalToggle
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { isExpanded = false }) {
                                Text("Close")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset Chips
                    Text("Quick Presets:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.activePreset == ChaosPresetType.FLAKY_3G,
                            onClick = { onApplyPreset(if (state.activePreset == ChaosPresetType.FLAKY_3G) ChaosPresetType.NONE else ChaosPresetType.FLAKY_3G) },
                            label = { Text("Flaky 3G") }
                        )
                        FilterChip(
                            selected = state.activePreset == ChaosPresetType.AUTH_401,
                            onClick = { onApplyPreset(if (state.activePreset == ChaosPresetType.AUTH_401) ChaosPresetType.NONE else ChaosPresetType.AUTH_401) },
                            label = { Text("Auth 401") }
                        )
                        FilterChip(
                            selected = state.activePreset == ChaosPresetType.SERVER_503,
                            onClick = { onApplyPreset(if (state.activePreset == ChaosPresetType.SERVER_503) ChaosPresetType.NONE else ChaosPresetType.SERVER_503) },
                            label = { Text("Server 503") }
                        )
                        FilterChip(
                            selected = state.activePreset == ChaosPresetType.RATE_LIMIT_429,
                            onClick = { onApplyPreset(if (state.activePreset == ChaosPresetType.RATE_LIMIT_429) ChaosPresetType.NONE else ChaosPresetType.RATE_LIMIT_429) },
                            label = { Text("Rate Limit 429") }
                        )
                        FilterChip(
                            selected = state.activePreset == ChaosPresetType.OFFLINE_MODE,
                            onClick = { onApplyPreset(if (state.activePreset == ChaosPresetType.OFFLINE_MODE) ChaosPresetType.NONE else ChaosPresetType.OFFLINE_MODE) },
                            label = { Text("Offline Mode") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Navigation Tabs: Rules vs Live Events
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Rules (${state.rules.size})") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Live Events (${state.events.size})") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab Content
                    if (selectedTab == 0) {
                        // Rules Tab
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active Rules", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Button(
                                onClick = {
                                    editingRule = null
                                    showRuleEditor = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("+ Add Rule", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.rules.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No chaos rules configured. Select a preset or add a custom rule.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.rules, key = { it.id }) { rule ->
                                    ChaosRuleCard(
                                        rule = rule,
                                        onToggle = { onToggleRule(rule.id) },
                                        onDelete = { onDeleteRule(rule.id) }
                                    )
                                }
                            }
                        }
                    } else {
                        // Live Events Tab
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Intercepted Events", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            TextButton(onClick = onClearEvents) {
                                Text("Clear Events")
                            }
                        }

                        if (state.events.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No network chaos events recorded yet.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.events, key = { it.id }) { event ->
                                    ChaosEventCard(event = event)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Rule Editor Dialog
        if (showRuleEditor) {
            ChaosRuleEditorDialog(
                initialRule = editingRule,
                onDismiss = { showRuleEditor = false },
                onSaveRule = {
                    onAddRule(it)
                    showRuleEditor = false
                }
            )
        }
    }
}

@Composable
private fun ChaosRuleCard(
    rule: ChaosRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (rule.enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
                    IconButton(onClick = onDelete) {
                        Text("🗑️", fontSize = 14.sp)
                    }
                }
            }

            Text(
                text = "URL: ${rule.urlPattern}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (rule.maxDelayMs > 0) {
                    Text("⏱️ ${rule.minDelayMs}–${rule.maxDelayMs}ms", fontSize = 12.sp, color = Color.Gray)
                }
                if (rule.dropConnection) {
                    Text("🔌 Drops Connection", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                } else if (rule.injectedStatusCode != null) {
                    Text("🚨 HTTP ${rule.injectedStatusCode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                Text("🎲 ${rule.failureProbabilityPercent}%", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ChaosEventCard(event: ChaosEvent) {
    val badgeColor = when (event.actionType) {
        ChaosActionType.DELAYED -> Color(0xFF2196F3)
        ChaosActionType.HTTP_ERROR_INJECTED -> Color(0xFFF44336)
        ChaosActionType.CONNECTION_DROPPED -> Color(0xFFFF9800)
        ChaosActionType.BYPASSED -> Color(0xFF4CAF50)
        ChaosActionType.NONE -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${event.method} ${event.requestUrl.takeLast(35)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = event.actionType.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = event.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================================
// PREVIEWS (Following sfc-android-compose-previews standards)
// ============================================================================

@PreviewLightDark
@Composable
private fun ComposeChaosOverlayContent_Populated_Preview() {
    MaterialTheme {
        Surface {
            ComposeChaosOverlayContent(
                state = ChaosConfigState(
                    globalEnabled = true,
                    activePreset = ChaosPresetType.FLAKY_3G,
                    rules = listOf(
                        ChaosRule(name = "3G Latency & Flakiness", urlPattern = ".*", minDelayMs = 1500, maxDelayMs = 3000, dropConnection = true, failureProbabilityPercent = 25)
                    ),
                    events = listOf(
                        ChaosEvent(requestUrl = "https://api.example.com/users", method = "GET", actionType = ChaosActionType.DELAYED, delayAppliedMs = 2100, description = "Injected 2100ms latency.")
                    )
                ),
                onGlobalToggle = {},
                onApplyPreset = {},
                onAddRule = {},
                onToggleRule = {},
                onDeleteRule = {},
                onClearEvents = {}
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ComposeChaosOverlayContent_Empty_Preview() {
    MaterialTheme {
        Surface {
            ComposeChaosOverlayContent(
                state = ChaosConfigState(),
                onGlobalToggle = {},
                onApplyPreset = {},
                onAddRule = {},
                onToggleRule = {},
                onDeleteRule = {},
                onClearEvents = {}
            )
        }
    }
}
