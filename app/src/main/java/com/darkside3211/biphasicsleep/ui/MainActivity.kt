package com.darkside3211.biphasicsleep.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.darkside3211.biphasicsleep.alarm.ArmedAlarmInfo
import com.darkside3211.biphasicsleep.alarm.HybridAlarmScheduler
import com.darkside3211.biphasicsleep.alarm.PendingAntiBackslideInfo
import com.darkside3211.biphasicsleep.domain.AdvisorySeverity
import com.darkside3211.biphasicsleep.domain.DeviationRecoveryCalculator
import com.darkside3211.biphasicsleep.domain.GuardrailPermission
import com.darkside3211.biphasicsleep.domain.NapLockoutEvaluation
import com.darkside3211.biphasicsleep.domain.PhysiologicalAdvisory
import com.darkside3211.biphasicsleep.domain.PrimaryAnchorWindowStatus
import com.darkside3211.biphasicsleep.domain.ProtocolStateId
import com.darkside3211.biphasicsleep.domain.SleepActionOption
import com.darkside3211.biphasicsleep.domain.SleepBlockType
import com.darkside3211.biphasicsleep.domain.WeekendLockoutStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val viewModel: BiphasicProtocolViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HybridAlarmScheduler.loadPersistedSessionState(applicationContext)
        setContent {
            BiphasicSleepTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBackground
                ) {
                    BiphasicSleepApp(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiphasicSleepApp(viewModel: BiphasicProtocolViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val armedAlarm by HybridAlarmScheduler.armedAlarm.collectAsState()
    val pendingAntiBackslide by HybridAlarmScheduler.pendingAntiBackslide.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showSimulationSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    LaunchedEffect(uiState.lastStatusToast) {
        val msg = uiState.lastStatusToast
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusToast()
        }
    }

    Scaffold(
        containerColor = ObsidianBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CircadianTopBar(
                uiState = uiState,
                onOpenSimulationSheet = { showSimulationSheet = true }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                contentColor = TextPrimary,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Bedtime, contentDescription = "Sleep") },
                    label = { Text("Sleep") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AnchorCyan,
                        selectedTextColor = AnchorCyan,
                        indicatorColor = Color(0xFF132238),
                        unselectedIconColor = MutedSlate,
                        unselectedTextColor = MutedSlate
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.AutoGraph, contentDescription = "Recovery") },
                    label = { Text("Recovery") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CircadianAmber,
                        selectedTextColor = CircadianAmber,
                        indicatorColor = Color(0xFF2C200E),
                        unselectedIconColor = MutedSlate,
                        unselectedTextColor = MutedSlate
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.DateRange, contentDescription = "Schedule") },
                    label = { Text("Schedule") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AnchorCyan,
                        selectedTextColor = AnchorCyan,
                        indicatorColor = Color(0xFF132238),
                        unselectedIconColor = MutedSlate,
                        unselectedTextColor = MutedSlate
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VerifiedEmerald,
                        selectedTextColor = VerifiedEmerald,
                        indicatorColor = Color(0xFF0F261C),
                        unselectedIconColor = MutedSlate,
                        unselectedTextColor = MutedSlate
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> SleepScreen(
                    uiState = uiState,
                    armedAlarm = armedAlarm,
                    pendingAntiBackslide = pendingAntiBackslide,
                    viewModel = viewModel,
                    onOpenRecoveryTab = { selectedTab = 1 }
                )
                1 -> RecoveryScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToSleep = { selectedTab = 0 }
                )
                2 -> ScheduleScreen(
                    uiState = uiState,
                    onSimulateState = { preset ->
                        viewModel.selectSimulationPreset(preset)
                        selectedTab = 0
                    }
                )
                3 -> SettingsScreen(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }

        if (showSimulationSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSimulationSheet = false },
                sheetState = sheetState,
                containerColor = ObsidianCardElevated,
                contentColor = TextPrimary
            ) {
                SimulationBottomSheetContent(
                    uiState = uiState,
                    onSelectPreset = { preset ->
                        viewModel.selectSimulationPreset(preset)
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            showSimulationSheet = false
                        }
                    },
                    onReplayIncident = {
                        viewModel.loadOct17To18IncidentReplay()
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            showSimulationSheet = false
                            selectedTab = 0
                        }
                    }
                )
            }
        }
    }
}

/**
 * Top Status Header showing live tabular time, circadian mode, and simulation status chip.
 */
@Composable
private fun CircadianTopBar(
    uiState: ProtocolUiState,
    onOpenSimulationSheet: () -> Unit
) {
    val isLive = uiState.activeSimulationPreset == SimulationPreset.LIVE_SYSTEM_CLOCK
    val state = uiState.phaseEvaluation.activeOrUpcomingState

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = ObsidianSurface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DeviationRecoveryCalculator.formatTime(uiState.currentClock),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.currentClock.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                        style = MaterialTheme.typography.labelSmall,
                        color = SoftSilver
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                val modeLabel = when {
                    uiState.phaseEvaluation.primaryAnchorWindow.isUnfulfilledAnchorDelayed ->
                        "Late Sleep Mode • Adjusted Cycles"
                    state.isWeekendMode ->
                        "Weekend Schedule"
                    else ->
                        "Work-Week Schedule (9 PM – 6 AM Shift)"
                }
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.isWeekendMode || uiState.phaseEvaluation.primaryAnchorWindow.isUnfulfilledAnchorDelayed) CircadianAmber else AnchorCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            // Tucked Simulation Chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isLive) Color(0xFF0F261E) else Color(0xFF2E200C))
                    .border(
                        1.dp,
                        if (isLive) VerifiedEmerald.copy(alpha = 0.5f) else CircadianAmber.copy(alpha = 0.7f),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onOpenSimulationSheet() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isLive) VerifiedEmerald else CircadianAmber)
                )
                Text(
                    text = if (isLive) "LIVE" else "SIMULATED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isLive) VerifiedEmerald else CircadianAmber,
                    fontSize = 11.sp
                )
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Configure Simulation",
                    tint = if (isLive) SoftSilver else CircadianAmber,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Tab 1: Sleep Screen (v2.0 Hard-Lock Guardrail & Single-Action Enforcement)
 */
@Composable
private fun SleepScreen(
    uiState: ProtocolUiState,
    armedAlarm: ArmedAlarmInfo?,
    pendingAntiBackslide: PendingAntiBackslideInfo?,
    viewModel: BiphasicProtocolViewModel,
    onOpenRecoveryTab: () -> Unit
) {
    val context = LocalContext.current
    val eval = uiState.phaseEvaluation
    var showEmergencyOverrideDialog by remember { mutableStateOf(false) }

    if (showEmergencyOverrideDialog) {
        EmergencyOverrideHoldDialog(
            onDismiss = { showEmergencyOverrideDialog = false },
            onArmOverride = { blockType ->
                viewModel.armSleepBlockAlarm(context, blockType)
                showEmergencyOverrideDialog = false
            },
            onNavigateToRecovery = {
                showEmergencyOverrideDialog = false
                onOpenRecoveryTab()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Event-Driven Session Completion Tracker Bar
        SessionCompletionTrackerBar(
            uiState = uiState,
            onLogSleepCompletedNow = {
                viewModel.recordAnchorCompleted(uiState.currentClock, context)
            },
            onClearSessionState = {
                viewModel.clearCompletedSessionHistory(context)
            }
        )

        // 2. Active Armed Alarm or 5-Minute Anti-Backslide Check-In
        if (pendingAntiBackslide != null) {
            PendingAntiBackslideCard(
                info = pendingAntiBackslide,
                onConfirmAwake = { HybridAlarmScheduler.confirmAntiBackslideAwake(context) },
                onFireNow = {
                    HybridAlarmScheduler.triggerImmediateTestChallenge(
                        context = context,
                        config = uiState.config,
                        isAntiBackslideCheck = true
                    )
                }
            )
        } else if (armedAlarm != null) {
            ArmedAlarmStatusCard(
                info = armedAlarm,
                onCancel = { HybridAlarmScheduler.cancelArmedAlarm(context) },
                onTestWakeChallengeNow = {
                    HybridAlarmScheduler.triggerImmediateTestChallenge(
                        context = context,
                        config = uiState.config,
                        isAntiBackslideCheck = false
                    )
                },
                onSendToSystemClock = {
                    HybridAlarmScheduler.dispatchAndroidSystemClockIntent(
                        context = context,
                        triggerAtMillis = armedAlarm.triggerAtMillis,
                        label = "Biphasic ${armedAlarm.stateId.title}: ${armedAlarm.blockType.shortTitle}",
                        skipUi = false
                    )
                }
            )
        }

        // 3. Zero-Nap Weekend or Deviation Lockout Banner
        if (eval.weekendLockoutStatus.isLockoutActive) {
            WeekendLockoutBanner(
                status = eval.weekendLockoutStatus,
                onOpenRecovery = onOpenRecoveryTab
            )
        }

        // 4. Top Primary Anchor Card: Hero Single-Action CTA during launch window or UNFULFILLED_ANCHOR_DELAYED,
        // or STATE_WAKE_LOCKOUT countdown card after sleep is completed
        if (eval.primaryAnchorWindow.isInActiveLaunchWindow && eval.primaryAnchorWindow.permittedActionOption != null) {
            val defaultOption = eval.primaryAnchorWindow.permittedActionOption
            val activeOption = eval.primaryAnchorWindow.availableCycleOptions
                .firstOrNull { it.blockType == uiState.selectedCustomCycleBlock }
                ?: defaultOption
            HeroPrimarySleepCard(
                primaryOption = activeOption,
                windowStatus = eval.primaryAnchorWindow,
                currentState = eval.primaryAnchorWindow.activeStateId,
                config = uiState.config,
                onSelectCycleBlock = { block ->
                    viewModel.selectCustomCycleBlock(block)
                },
                onArmPrimary = {
                    viewModel.armSleepBlockAlarm(context, activeOption.blockType)
                },
                onArmSystemClock = {
                    viewModel.armSleepBlockAlarm(
                        context = context,
                        blockType = activeOption.blockType,
                        forceSystemClockIntent = true
                    )
                }
            )
        } else {
            WakeLockoutHeroCard(
                status = eval.primaryAnchorWindow,
                onOpenEmergencyOverride = { showEmergencyOverrideDialog = true },
                onOpenRecovery = onOpenRecoveryTab,
                onUnlockIfNotSlept = if (eval.primaryAnchorWindow.isAnchorCompletedForCurrentCycle) {
                    { viewModel.clearCompletedSessionHistory(context) }
                } else {
                    null
                }
            )
        }

        // 5. Dynamic 20-Minute Adenosine Flush Nap Card (Sliding Eligibility Window)
        DynamicNapFlushCard(
            napEvaluation = eval.napEvaluation,
            onArmNap = {
                viewModel.armSleepBlockAlarm(context, SleepBlockType.FLUSH_NAP_20M)
            },
            onArmSystemClock = {
                viewModel.armSleepBlockAlarm(
                    context = context,
                    blockType = SleepBlockType.FLUSH_NAP_20M,
                    forceSystemClockIntent = true
                )
            },
            onMarkNapCompleted = {
                viewModel.recordNapCompleted(uiState.currentClock, context)
            }
        )

        // 5. Active Physiological Rules
        if (eval.advisories.isNotEmpty()) {
            Text(
                text = "Circadian & Physiological Rules",
                style = MaterialTheme.typography.titleMedium,
                color = SoftSilver,
                modifier = Modifier.padding(top = 4.dp)
            )

            eval.advisories.forEach { advisory ->
                PhysiologicalRuleCard(advisory = advisory)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Compact Session Completion Status & 1-Tap Manual Wake/Reset Bar (v2.2 Event-Driven).
 * Prevents phantom wall-clock lockouts while letting the user mark sleep complete if they used the OS Clock.
 */
@Composable
private fun SessionCompletionTrackerBar(
    uiState: ProtocolUiState,
    onLogSleepCompletedNow: () -> Unit,
    onClearSessionState: () -> Unit
) {
    val windowStatus = uiState.phaseEvaluation.primaryAnchorWindow
    val napEval = uiState.phaseEvaluation.napEvaluation
    val hasCompletedSleep = windowStatus.isAnchorCompletedForCurrentCycle
    val hasCompletedNap = napEval.isNapCompletedInCurrentPeriod
    val lastWake = uiState.activeSimulationPreset.simulatedLastWakeTime ?: uiState.lastWakeTimestamp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (hasCompletedSleep || hasCompletedNap) VerifiedEmerald.copy(alpha = 0.45f) else ObsidianBorderSubtle,
                RoundedCornerShape(14.dp)
            ),
        color = if (hasCompletedSleep || hasCompletedNap) VerifiedEmeraldBg.copy(alpha = 0.45f) else ObsidianCardElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = when {
                        hasCompletedSleep && lastWake != null ->
                            "SLEEP LOGGED • WOKE AT ${DeviationRecoveryCalculator.formatTime(lastWake)}"
                        hasCompletedNap && lastWake != null ->
                            "20M NAP LOGGED AT ${DeviationRecoveryCalculator.formatTime(lastWake)}"
                        else ->
                            "NO SLEEP LOGGED THIS CYCLE (0m)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (hasCompletedSleep || hasCompletedNap) VerifiedEmerald else SoftSilver,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (hasCompletedSleep || hasCompletedNap) {
                        "Post-wake cooldown & schedule updated from your confirmed wake event."
                    } else {
                        "Used another alarm app? Tap 'I Just Woke Up' when you finish sleeping."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            if (hasCompletedSleep || hasCompletedNap || lastWake != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF28191D),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LockoutCrimson.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onClearSessionState() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = LockoutCrimson,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = LockoutCrimson
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VerifiedEmeraldBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { onLogSleepCompletedNow() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = VerifiedEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "I Just Woke Up",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = VerifiedEmerald
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hero Primary Sleep Card with the custom CircadianArcGauge, Dynamic Cycle Truncation Banner,
 * Shifted Next Sleep Anchor indicator, and 1-Tap Cycle Switcher (3c / 2c / 1c).
 */
@Composable
private fun HeroPrimarySleepCard(
    primaryOption: SleepActionOption,
    windowStatus: PrimaryAnchorWindowStatus,
    currentState: ProtocolStateId,
    config: com.darkside3211.biphasicsleep.domain.SleepProtocolConfig,
    onSelectCycleBlock: (SleepBlockType) -> Unit,
    onArmPrimary: () -> Unit,
    onArmSystemClock: () -> Unit
) {
    val wakeStr = DeviationRecoveryCalculator.formatTime(primaryOption.calculatedWakeTime)
    val isDelayedMode = windowStatus.isUnfulfilledAnchorDelayed
    val accentColor = if (isDelayedMode || currentState.isWeekendMode) CircadianAmber else AnchorCyan

    BiometricCard(
        borderColor = accentColor.copy(alpha = 0.5f),
        backgroundColor = ObsidianCard
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = if (isDelayedMode) {
                        windowStatus.delayedRecoveryHeadline ?: currentState.title
                    } else {
                        currentState.title
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = if (isDelayedMode) {
                        "Next Sleep Target: ${DeviationRecoveryCalculator.formatTime(windowStatus.nextScheduledAnchorTime)}" +
                            if (windowStatus.isAnchorPushedBack) {
                                " (Shifted from ${DeviationRecoveryCalculator.formatTime(windowStatus.nominalNextAnchorTime)})"
                            } else {
                                ""
                            }
                    } else {
                        currentState.scheduleWindowLabel
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (windowStatus.isAnchorPushedBack) CircadianAmber else SoftSilver,
                    fontWeight = if (windowStatus.isAnchorPushedBack) FontWeight.Bold else FontWeight.Normal
                )
            }
            StatusBadgePill(permission = primaryOption.permission)
        }

        // Dynamic Next Sleep Anchor Shift Pill when late arrival pushes 01:00 -> 02:00
        if (windowStatus.isAnchorPushedBack) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF281B0D),
                border = androidx.compose.foundation.BorderStroke(1.dp, CircadianAmber.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = CircadianAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "NEXT SLEEP ANCHOR SHIFTED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = CircadianAmber
                        )
                    }
                    Text(
                        text = "${DeviationRecoveryCalculator.formatTime(windowStatus.nominalNextAnchorTime)} -> ${DeviationRecoveryCalculator.formatTime(windowStatus.nextScheduledAnchorTime)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Center Gauge with Tabular Numerals
        CircadianArcGauge(
            progress = when (primaryOption.blockType) {
                SleepBlockType.FLUSH_NAP_20M -> 0.35f
                SleepBlockType.RECOVERY_1_CYCLE -> 0.55f
                SleepBlockType.BRIDGE_2_CYCLES -> 0.78f
                SleepBlockType.CORE_3_CYCLES -> 1f
            },
            arcColor = accentColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "WAKE UP AT",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftSilver,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = wakeStr,
                    style = MaterialTheme.typography.displayLarge,
                    color = TextPrimary
                )
                Text(
                    text = "${DeviationRecoveryCalculator.formatDuration( java.time.Duration.ofMinutes(primaryOption.durationMinutes.toLong()))} (${primaryOption.durationMinutes}m)",
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Segmented Cycle Breakdown Bar
        CycleBreakdownBar(
            blockType = primaryOption.blockType,
            latencyMinutes = config.latencyBufferMinutes,
            cycleMinutes = config.cycleDurationMinutes,
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Plain-English Explanation & Weekday 6:00 PM Commute Note
        Text(
            text = primaryOption.guardrailReason,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        if (windowStatus.leewayAdvisoryText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = windowStatus.leewayAdvisoryText,
                style = MaterialTheme.typography.bodySmall,
                color = AnchorCyan,
                fontSize = 11.sp
            )
        }

        // 1-Tap Flexible Cycle Switcher (3 Cycles / 2 Cycles / 1 Cycle)
        if (windowStatus.availableCycleOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ADJUST SLEEP LENGTH (1-TAP SWITCH):",
                style = MaterialTheme.typography.labelSmall,
                color = SoftSilver,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                windowStatus.availableCycleOptions.forEach { option ->
                    val isSelected = option.blockType == primaryOption.blockType
                    val isRecommended = option.blockType == windowStatus.permittedBlockType
                    val optWakeStr = DeviationRecoveryCalculator.formatTime(option.calculatedWakeTime)
                    val optDurStr = DeviationRecoveryCalculator.formatDuration(
                        java.time.Duration.ofMinutes(option.durationMinutes.toLong())
                    )
                    val pillColor = if (isSelected) accentColor else SoftSilver

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) accentColor else ObsidianBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectCycleBlock(option.blockType) },
                        color = if (isSelected) accentColor.copy(alpha = 0.16f) else ObsidianCardElevated
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${option.blockType.cyclesCount}c • $optDurStr",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = pillColor,
                                maxLines = 1
                            )
                            Text(
                                text = "Wake $optWakeStr" + if (isRecommended) " ★" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) TextPrimary else MutedSlate,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large 54dp Action Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onArmPrimary,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${primaryOption.blockType.ctaLabel} ($wakeStr)",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
            }

            OutlinedButton(
                onClick = onArmSystemClock,
                modifier = Modifier.height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Alarm,
                    contentDescription = "Set OS Alarm",
                    tint = AnchorCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "OS Clock",
                    style = MaterialTheme.typography.labelMedium,
                    color = AnchorCyan,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Top Primary Card during STATE_WAKE_LOCKOUT.
 * Renders a plain-English Stay Awake countdown card to the next scheduled sleep.
 */
@Composable
private fun WakeLockoutHeroCard(
    status: PrimaryAnchorWindowStatus,
    onOpenEmergencyOverride: () -> Unit,
    onOpenRecovery: () -> Unit,
    onUnlockIfNotSlept: (() -> Unit)? = null
) {
    BiometricCard(
        borderColor = LockoutCrimson.copy(alpha = 0.5f),
        backgroundColor = ObsidianCard
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = LockoutCrimson,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STAY AWAKE WINDOW",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = LockoutCrimson,
                    maxLines = 1
                )
            }
            StatusBadgePill(permission = GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (status.isAnchorCompletedForCurrentCycle) {
                "You already finished your sleep for this period"
            } else {
                "Stay awake so you can fall asleep easily at your next target time"
            },
            style = MaterialTheme.typography.bodySmall,
            color = SoftSilver
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Center Gauge with Tabular Numerals displaying live countdown to next anchor
        val countdownStr = DeviationRecoveryCalculator.formatDuration(status.countdownToNextAnchor)
        val nextTimeStr = DeviationRecoveryCalculator.formatTime(status.nextScheduledAnchorTime)

        CircadianArcGauge(
            progress = 0.82f,
            arcColor = CircadianAmber,
            trackColor = Color(0xFF26190D),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NEXT SLEEP IN",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftSilver,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = countdownStr,
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "AT $nextTimeStr",
                    style = MaterialTheme.typography.titleSmall,
                    color = CircadianAmber,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Upcoming: ${status.nextScheduledAnchorState.title} ($nextTimeStr)",
            style = MaterialTheme.typography.labelMedium,
            color = CircadianAmber,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Main sleep is paused so you stay tired enough for ${status.nextScheduledAnchorState.title} at $nextTimeStr. Sleeping a full cycle right now will make it hard to sleep later.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpenEmergencyOverride,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF452B10)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = CircadianAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Sleep Anyway",
                    style = MaterialTheme.typography.labelMedium,
                    color = CircadianAmber,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            if (onUnlockIfNotSlept != null) {
                OutlinedButton(
                    onClick = onUnlockIfNotSlept,
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnchorCyan.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Didn't Sleep? Unlock",
                        color = AnchorCyan,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onOpenRecovery,
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Recovery Tab",
                        color = SoftSilver,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Dynamic 20-Minute Power Nap Card (v2.2 Sliding Eligibility Window).
 * Unlocked throughout shift/wake window until taken or within 4h of next core sleep.
 */
@Composable
private fun DynamicNapFlushCard(
    napEvaluation: NapLockoutEvaluation,
    onArmNap: () -> Unit,
    onArmSystemClock: () -> Unit,
    onMarkNapCompleted: () -> Unit
) {
    val isLocked = napEvaluation.isHardLocked
    val accent = if (isLocked) LockoutCrimson else AnchorCyan
    val bg = if (isLocked) LockoutCrimsonBg.copy(alpha = 0.35f) else ObsidianCardElevated

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.dp,
                if (isLocked) LockoutCrimson.copy(alpha = 0.4f) else AnchorCyan.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            ),
        color = bg
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.Coffee,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "20-MIN POWER NAP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = accent,
                        maxLines = 1
                    )
                }
                StatusBadgePill(
                    permission = if (isLocked) GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL else GuardrailPermission.PRIMARY_RECOMMENDED
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = napEvaluation.statusHeadline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = napEvaluation.lockoutReason,
                style = MaterialTheme.typography.bodySmall,
                color = if (isLocked) Color(0xFFCBD5E1) else SoftSilver
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onArmNap,
                    enabled = !isLocked,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnchorCyan,
                        disabledContainerColor = Color(0xFF1E1E26)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = if (isLocked) MutedSlate else Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLocked) {
                            "Nap Locked"
                        } else {
                            "Start 20m Nap (+20m)"
                        },
                        color = if (isLocked) MutedSlate else Color.Black,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (!isLocked) {
                    OutlinedButton(
                        onClick = onArmSystemClock,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Alarm,
                            contentDescription = "OS Clock",
                            tint = AnchorCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = onMarkNapCompleted,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "Done",
                            color = SoftSilver,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5-Second Continuous Long-Press Confirmation Modal (Section 4.B).
 * Enforces physiological awareness and active physical hold before off-schedule sleep can be armed.
 */
@Composable
private fun EmergencyOverrideHoldDialog(
    onDismiss: () -> Unit,
    onArmOverride: (SleepBlockType) -> Unit,
    onNavigateToRecovery: () -> Unit
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        if (isHolding && !isUnlocked) {
            val startTime = System.currentTimeMillis()
            val holdDuration = 5000L
            while (isHolding && !isUnlocked) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed.toFloat() / holdDuration).coerceIn(0f, 1f)
                if (elapsed >= holdDuration) {
                    isUnlocked = true
                    holdProgress = 1f
                    break
                }
                delay(40L)
            }
        } else {
            if (!isUnlocked) {
                holdProgress = 0f
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ObsidianCardElevated,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Security,
                    contentDescription = null,
                    tint = if (isUnlocked) VerifiedEmerald else LockoutCrimson,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isUnlocked) "Override Unlocked" else "Emergency Override Guardrail",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "CIRCADIAN IMPACT WARNING: Initiating an off-schedule full-cycle sleep outside your scheduled launch window bleeds off homeostatic sleep pressure, destabilizes your circadian anchor, and guarantees pre-shift insomnia before your next work shift.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CircadianAmber
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (!isUnlocked) {
                    Text(
                        text = "To prevent groggy or accidental taps while half-asleep, press and hold continuously for 5.0 seconds to unlock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftSilver
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF26190D))
                            .border(1.dp, CircadianAmber.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHolding = true
                                        tryAwaitRelease()
                                        isHolding = false
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (holdProgress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(holdProgress)
                                    .align(Alignment.CenterStart)
                                    .background(CircadianAmber.copy(alpha = 0.45f))
                            )
                        }
                        Text(
                            text = if (isHolding) {
                                "HOLDING... ${(holdProgress * 5).toInt()}/5s"
                            } else {
                                "PRESS & HOLD (5.0s) TO UNLOCK"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = CircadianAmber
                        )
                    }
                } else {
                    Text(
                        text = "Override Unlocked — Select off-schedule sleep action:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = VerifiedEmerald
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onArmOverride(SleepBlockType.CORE_3_CYCLES) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LockoutCrimson),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Force Arm 3-Cycle Core (+4h 44m)", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onArmOverride(SleepBlockType.BRIDGE_2_CYCLES) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38290E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Force Arm 2-Cycle Bridge (+3h 14m)", color = CircadianAmber, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onNavigateToRecovery) {
                        Text("Open Recovery Tab", color = AnchorCyan)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SoftSilver)
                    }
                }
            }
        }
    }
}

/**
 * Weekend or Deviation Lockout Banner with countdown to anchor.
 */
@Composable
private fun WeekendLockoutBanner(
    status: WeekendLockoutStatus,
    onOpenRecovery: () -> Unit
) {
    val isDangerZone = status.isInEveningBoredomDangerZone
    val accent = if (isDangerZone) LockoutCrimson else CircadianAmber
    val bg = if (isDangerZone) LockoutCrimsonBg else Color(0xFF261A0C)

    BiometricCard(
        borderColor = accent,
        backgroundColor = bg
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isDangerZone) "Boredom Danger Zone (18:00–23:00)" else "Zero-Nap Lockout Active",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "COUNTDOWN TO 01:00 ANCHOR",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftSilver
                )
                Text(
                    text = DeviationRecoveryCalculator.formatDuration(status.countdownUntilAnchor),
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "NEXT SLEEP",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftSilver
                )
                Text(
                    text = DeviationRecoveryCalculator.formatDayTime(status.nextAnchorTimestamp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = status.subtext,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/**
 * Armed Alarm status card.
 */
@Composable
private fun ArmedAlarmStatusCard(
    info: ArmedAlarmInfo,
    onCancel: () -> Unit,
    onTestWakeChallengeNow: () -> Unit,
    onSendToSystemClock: () -> Unit
) {
    BiometricCard(
        borderColor = VerifiedEmerald,
        backgroundColor = VerifiedEmeraldBg
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = VerifiedEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ARMED ALARM • ${info.blockType.shortTitle.uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = VerifiedEmerald
                )
            }
            Text(
                text = "+${info.durationMinutes}m",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Wake Target: ${DeviationRecoveryCalculator.formatDayTime(info.targetWakeDateTime)}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        val activeChallenges = buildList {
            if (info.requireStepChallenge) add("${info.requiredSteps} Steps")
            if (info.requireBarcodeChallenge) add("Barcode Scan")
            if (info.enableFiveMinAntiBackslide) add("5m Anti-Backslide Check")
        }.joinToString(" • ").ifEmpty { "Standard Dismissal" }

        Text(
            text = "Verification: $activeChallenges",
            style = MaterialTheme.typography.bodySmall,
            color = SoftSilver
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onTestWakeChallengeNow,
                colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Simulate Ring Now", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onSendToSystemClock,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("OS Clock", color = AnchorCyan)
            }
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Rounded.AlarmOff,
                    contentDescription = "Disarm",
                    tint = LockoutCrimson
                )
            }
        }
    }
}

/**
 * 5-Minute Anti-Backslide Check-In armed card.
 */
@Composable
private fun PendingAntiBackslideCard(
    info: PendingAntiBackslideInfo,
    onConfirmAwake: () -> Unit,
    onFireNow: () -> Unit
) {
    BiometricCard(
        borderColor = CircadianAmber,
        backgroundColor = Color(0xFF2C190D)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = CircadianAmber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ANTI-BACKSLIDE 5-MINUTE CHECK-IN",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = CircadianAmber
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Re-Alarm Fires At ${DeviationRecoveryCalculator.formatTime(info.checkInDateTime)}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Confirm you left bed before ${DeviationRecoveryCalculator.formatTime(info.checkInDateTime)} or full alarm will sound again.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onConfirmAwake,
                colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirm Out of Bed", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onFireNow,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Test Ring", color = CircadianAmber)
            }
        }
    }
}

/**
 * Active physiological rule card with vector icon.
 */
@Composable
private fun PhysiologicalRuleCard(advisory: PhysiologicalAdvisory) {
    val (icon, tint) = when (advisory.severity) {
        AdvisorySeverity.HIGH_ALERT_LOCKOUT -> Icons.Rounded.ShieldMoon to LockoutCrimson
        AdvisorySeverity.PHYSIOLOGICAL_TRIGGER -> {
            if (advisory.title.contains("Sunglasses", ignoreCase = true)) {
                Icons.Rounded.WbTwilight to CircadianAmber
            } else if (advisory.title.contains("Caffeine", ignoreCase = true)) {
                Icons.Rounded.Coffee to CircadianAmber
            } else {
                Icons.Rounded.WarningAmber to CircadianAmber
            }
        }
        AdvisorySeverity.INFO -> Icons.Rounded.Schedule to AnchorCyan
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        color = ObsidianCardElevated
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Column {
                Text(
                    text = advisory.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = advisory.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Tab 2: Recovery Screen (Deviation Calculator with Native Material 3 TimePicker)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTimeAdjustButton(
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = accentColor.copy(alpha = 0.12f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryScreen(
    uiState: ProtocolUiState,
    viewModel: BiphasicProtocolViewModel,
    onNavigateToSleep: () -> Unit
) {
    val plan = uiState.computedDeviationPlan
    var showOnsetPicker by remember { mutableStateOf(false) }
    var showWakePicker by remember { mutableStateOf(false) }

    val onsetTime = plan.input.sleepStartTimestamp.toLocalTime()
    val wakeTime = plan.input.actualWakeTimestamp.toLocalTime()

    if (showOnsetPicker) {
        val state = rememberTimePickerState(
            initialHour = onsetTime.hour,
            initialMinute = onsetTime.minute,
            is24Hour = true
        )
        BiometricTimePickerDialog(
            title = "Select Sleep Onset Time",
            onDismiss = { showOnsetPicker = false },
            onConfirm = {
                viewModel.setDeviationSleepStartTime(state.hour, state.minute)
                showOnsetPicker = false
            }
        ) {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = ObsidianCardElevated,
                    clockDialSelectedContentColor = Color.Black,
                    clockDialUnselectedContentColor = TextPrimary,
                    selectorColor = AnchorCyan,
                    containerColor = ObsidianCard,
                    periodSelectorBorderColor = ObsidianBorder,
                    timeSelectorSelectedContainerColor = AnchorCyan,
                    timeSelectorUnselectedContainerColor = ObsidianCardElevated,
                    timeSelectorSelectedContentColor = Color.Black,
                    timeSelectorUnselectedContentColor = TextPrimary
                )
            )
        }
    }

    if (showWakePicker) {
        val state = rememberTimePickerState(
            initialHour = wakeTime.hour,
            initialMinute = wakeTime.minute,
            is24Hour = true
        )
        BiometricTimePickerDialog(
            title = "Select Actual Wake Time",
            onDismiss = { showWakePicker = false },
            onConfirm = {
                viewModel.setDeviationWakeTime(state.hour, state.minute)
                showWakePicker = false
            }
        ) {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = ObsidianCardElevated,
                    clockDialSelectedContentColor = Color.Black,
                    clockDialUnselectedContentColor = TextPrimary,
                    selectorColor = VerifiedEmerald,
                    containerColor = ObsidianCard,
                    periodSelectorBorderColor = ObsidianBorder,
                    timeSelectorSelectedContainerColor = VerifiedEmerald,
                    timeSelectorUnselectedContainerColor = ObsidianCardElevated,
                    timeSelectorSelectedContentColor = Color.Black,
                    timeSelectorUnselectedContentColor = TextPrimary
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Interactive Sleep Onset & Wake Pickers
        BiometricCard(
            borderColor = AnchorCyan.copy(alpha = 0.4f),
            backgroundColor = ObsidianCard
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Log Sleep Deviation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Recompute circadian recovery & lockouts",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftSilver,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = VerifiedEmeraldBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { viewModel.setDeviationWakeToNow() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = VerifiedEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Woke Up Now",
                            color = VerifiedEmerald,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2-Column Time Selection Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sleep Onset Card
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                        .clickable { showOnsetPicker = true },
                    color = ObsidianCardElevated
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SLEEP ONSET",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftSilver,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Edit Sleep Onset",
                                tint = AnchorCyan.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DeviationRecoveryCalculator.formatTime(plan.input.sleepStartTimestamp),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = AnchorCyan
                        )
                        Text(
                            text = plan.input.sleepStartTimestamp.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSlate
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuickTimeAdjustButton(
                                label = "-30m",
                                accentColor = AnchorCyan,
                                onClick = { viewModel.adjustDeviationSleepStartMinutes(-30) },
                                modifier = Modifier.weight(1f)
                            )
                            QuickTimeAdjustButton(
                                label = "+30m",
                                accentColor = AnchorCyan,
                                onClick = { viewModel.adjustDeviationSleepStartMinutes(30) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Actual Wake Card
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                        .clickable { showWakePicker = true },
                    color = ObsidianCardElevated
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTUAL WAKE",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftSilver,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Edit Actual Wake",
                                tint = VerifiedEmerald.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DeviationRecoveryCalculator.formatTime(plan.input.actualWakeTimestamp),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = VerifiedEmerald
                        )
                        Text(
                            text = plan.input.actualWakeTimestamp.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSlate
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuickTimeAdjustButton(
                                label = "-15m",
                                accentColor = VerifiedEmerald,
                                onClick = { viewModel.adjustDeviationWakeTimeMinutes(-15) },
                                modifier = Modifier.weight(1f)
                            )
                            QuickTimeAdjustButton(
                                label = "+15m",
                                accentColor = VerifiedEmerald,
                                onClick = { viewModel.adjustDeviationWakeTimeMinutes(15) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Enforce Override on Sleep Screen Switch
            val isOverrideActive = uiState.isDeviationOverrideAppliedToDeck
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        if (isOverrideActive) AnchorCyan.copy(alpha = 0.5f) else ObsidianBorderSubtle,
                        RoundedCornerShape(14.dp)
                    ),
                color = if (isOverrideActive) Color(0xFF132238) else ObsidianCardElevated
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "Apply Recovery Override to Sleep Screen",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isOverrideActive) {
                                "ACTIVE • Overriding home screen with zero-nap lockout and overnight 2-cycle bridge cap."
                            } else {
                                "Enforces zero-nap lockout and overnight 2-cycle bridge caps on the home screen."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverrideActive) AnchorCyan else SoftSilver,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isOverrideActive,
                        onCheckedChange = { viewModel.toggleApplyDeviationToDeck(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AnchorCyan,
                            checkedTrackColor = Color(0xFF0C2C3D)
                        )
                    )
                }
            }
        }

        // Active Recovery Strategy Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    if (plan.enforceZeroNapLockout) CircadianAmber.copy(alpha = 0.5f) else AnchorCyan.copy(alpha = 0.4f),
                    RoundedCornerShape(12.dp)
                ),
            color = if (plan.enforceZeroNapLockout) Color(0xFF26190D) else Color(0xFF0E1E2B)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (plan.enforceZeroNapLockout) Icons.Rounded.ShieldMoon else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = if (plan.enforceZeroNapLockout) CircadianAmber else AnchorCyan,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "ACTIVE RECOVERY STRATEGY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (plan.enforceZeroNapLockout) CircadianAmber else AnchorCyan,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    )
                    Text(
                        text = plan.headline,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        // 4-Tile Biometric Metric Grid
        Text(
            text = "Computed Recovery Metrics",
            style = MaterialTheme.typography.titleMedium,
            color = SoftSilver
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = "Total Sleep",
                value = DeviationRecoveryCalculator.formatDuration(plan.actualSleepDuration),
                subtitle = "~${plan.estimatedCyclesCompleted} Cycles + 14m",
                accentColor = CircadianAmber,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Bedtime
            )
            MetricTile(
                title = "Wake Delta",
                value = DeviationRecoveryCalculator.formatDuration(plan.wakeWindowDeltaFromBaseline),
                subtitle = "vs Baseline",
                accentColor = TextPrimary,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Schedule
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = "Time Until Anchor",
                value = DeviationRecoveryCalculator.formatDuration(plan.timeUntilNextAnchor),
                subtitle = DeviationRecoveryCalculator.formatDayTime(plan.nextAnchorTimestamp),
                accentColor = AnchorCyan,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Lock
            )
            MetricTile(
                title = "Overnight Cap",
                value = if (plan.capPrecedingOvernightToTwoCycles) "2 Cycles" else "Standard 3c",
                subtitle = if (plan.capPrecedingOvernightToTwoCycles) "3h 14m Cap" else "Normal",
                accentColor = if (plan.capPrecedingOvernightToTwoCycles) LockoutCrimson else VerifiedEmerald,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ShieldMoon
            )
        }

        // Vertical Connected Recovery Timeline
        Text(
            text = "Recovery Protocol Steps",
            style = MaterialTheme.typography.titleMedium,
            color = SoftSilver
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            plan.executedRecoverySteps.forEachIndexed { index, step ->
                VerticalTimelineStepItem(
                    step = step,
                    isLast = index == plan.executedRecoverySteps.lastIndex
                )
            }
        }

        // Oct 17–18 Incident Log Replay Card
        BiometricCard(
            borderColor = CircadianAmber.copy(alpha = 0.5f),
            backgroundColor = Color(0xFF22170D)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OCT 17–18 INCIDENT TEST CASE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = CircadianAmber
                )
                StatusBadgePill(permission = GuardrailPermission.WARN_INSOMNIA_OR_CIRCADIAN_RISK)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Accidental Nap Trap Simulator",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Replay the incident: Sat Oct 17 20:00 nap became a 5-cycle sleep, waking Sun 04:27. Enforces 20h 33m zero-nap lockout and Mon 01:00 2-cycle bridge cap.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF332010),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CircadianAmber.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "20:00 Sat Onset",
                        color = CircadianAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF332010),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CircadianAmber.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "04:27 Sun Wake",
                        color = VerifiedEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF332010),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CircadianAmber.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "5 Cycles (8h 27m)",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.loadOct17To18IncidentReplay()
                    onNavigateToSleep()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CircadianAmber),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Replay Incident on Sleep Screen", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Metric tile for the 4-tile grid.
 */
@Composable
private fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp)),
        color = ObsidianCardElevated
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = SoftSilver, fontWeight = FontWeight.Bold)
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MutedSlate, fontSize = 11.sp)
        }
    }
}

/**
 * Step in the vertical connected recovery timeline.
 */
@Composable
private fun VerticalTimelineStepItem(
    step: com.darkside3211.biphasicsleep.domain.RecoveryStep,
    isLast: Boolean
) {
    val nodeColor = if (step.isCriticalGuardrail) CircadianAmber else AnchorCyan

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Vertical line with circular node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(nodeColor.copy(alpha = 0.2f))
                    .border(2.dp, nodeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${step.stepNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = nodeColor,
                    fontSize = 12.sp
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(ObsidianBorder)
                )
            }
        }

        // Step Content Card
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    if (step.isCriticalGuardrail) CircadianAmber.copy(alpha = 0.5f) else ObsidianBorderSubtle,
                    RoundedCornerShape(16.dp)
                ),
            color = ObsidianCardElevated
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top metadata row: Time window pill on left + Guardrail pill on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time Window Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = nodeColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, nodeColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = nodeColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = step.windowLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = nodeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Status Badge Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (step.isCriticalGuardrail) LockoutCrimsonBg else Color(0xFF132238),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (step.isCriticalGuardrail) CircadianAmber.copy(alpha = 0.5f) else AnchorCyan.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = if (step.isCriticalGuardrail) "CRITICAL GUARDRAIL" else "PROTOCOL STEP",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (step.isCriticalGuardrail) CircadianAmber else AnchorCyan,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Step Title (Full width, no crushing or vertical character-wrapping)
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Step Description
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Tab 3: Schedule Screen (Weekly Circadian Architecture with 24h Visual Bars)
 */
@Composable
private fun ScheduleScreen(
    uiState: ProtocolUiState,
    onSimulateState: (SimulationPreset) -> Unit
) {
    val cfg = uiState.config

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Core Constants Grid
        Text(
            text = "Core Ultradian Parameters",
            style = MaterialTheme.typography.titleMedium,
            color = SoftSilver
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                title = "Cycle Length",
                value = "${cfg.cycleDurationMinutes}m",
                subtitle = "Ultradian Cycle",
                accentColor = AnchorCyan,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Onset Latency",
                value = "${cfg.latencyBufferMinutes}m",
                subtitle = "Buffer Buffer",
                accentColor = SoftSilver,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "3-Cycle Core",
                value = "4h 44m",
                subtitle = "284 minutes",
                accentColor = VerifiedEmerald,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "2-Cycle Bridge",
                value = "3h 14m",
                subtitle = "194 minutes",
                accentColor = CircadianAmber,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Weekly State Machine Phases",
            style = MaterialTheme.typography.titleMedium,
            color = SoftSilver,
            modifier = Modifier.padding(top = 4.dp)
        )

        ProtocolStateId.entries.forEach { stateId ->
            val presetForState = when (stateId) {
                ProtocolStateId.STATE_WORK_CORE -> SimulationPreset.WORK_CORE_MON_1230
                ProtocolStateId.STATE_WORK_NAP -> SimulationPreset.WORK_NAP_TUE_0115
                ProtocolStateId.STATE_BRIDGE_TO_WKND -> SimulationPreset.SAT_BRIDGE_0800
                ProtocolStateId.STATE_WKND_CORE -> SimulationPreset.SAT_BOREDOM_LOCKOUT_2000
                ProtocolStateId.STATE_BRIDGE_TO_WORK -> SimulationPreset.MON_BRIDGE_0100
                ProtocolStateId.UNFULFILLED_ANCHOR_DELAYED -> SimulationPreset.SAT_BRIDGE_0800
                ProtocolStateId.STATE_WAKE_LOCKOUT -> SimulationPreset.INCIDENT_2_WAKE_LOCKOUT_0605
            }

            val (sleepStart, sleepEnd) = when (stateId) {
                ProtocolStateId.STATE_WORK_CORE -> cfg.workCoreStart to cfg.workCoreEnd
                ProtocolStateId.STATE_WORK_NAP -> cfg.workNapStart to cfg.workNapEnd
                ProtocolStateId.STATE_BRIDGE_TO_WKND -> cfg.satBridgeStart to cfg.satBridgeEnd
                ProtocolStateId.STATE_WKND_CORE -> cfg.wkndCoreStart to cfg.wkndCoreEnd
                ProtocolStateId.STATE_BRIDGE_TO_WORK -> cfg.monBridgeStart to cfg.monBridgeEnd
                ProtocolStateId.UNFULFILLED_ANCHOR_DELAYED -> null to null
                ProtocolStateId.STATE_WAKE_LOCKOUT -> null to null
            }

            BiometricCard(
                borderColor = if (stateId.isWeekendMode) CircadianAmber.copy(alpha = 0.5f) else AnchorCyan.copy(alpha = 0.35f),
                backgroundColor = ObsidianCard
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stateId.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${stateId.dayPhaseLabel} • ${stateId.scheduleWindowLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SoftSilver
                        )
                    }
                    Text(
                        text = stateId.durationLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (stateId.isWeekendMode) CircadianAmber else AnchorCyan
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 24-Hour Visual Schedule Track
                TwentyFourHourTimelineBar(
                    sleepStart = sleepStart,
                    sleepEnd = sleepEnd,
                    highlightColor = if (stateId.isWeekendMode) CircadianAmber else AnchorCyan
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stateId.systemRulesSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { onSimulateState(presetForState) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Simulate This Phase", color = AnchorCyan, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Tab 4: Settings Screen (Wake-Up Challenges & Timing Parameters)
 */
@Composable
private fun SettingsScreen(
    uiState: ProtocolUiState,
    viewModel: BiphasicProtocolViewModel
) {
    val context = LocalContext.current
    val cfg = uiState.config
    var barcodeText by remember(cfg.registeredBarcodeValue) {
        mutableStateOf(cfg.registeredBarcodeValue ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Test Triggers Card
        BiometricCard(
            borderColor = VerifiedEmerald.copy(alpha = 0.5f),
            backgroundColor = VerifiedEmeraldBg
        ) {
            Text(
                text = "Interactive Test Harness",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VerifiedEmerald
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Trigger lock-screen wake-up challenges immediately to test physical sensor and camera workflows.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        HybridAlarmScheduler.triggerImmediateTestChallenge(
                            context = context,
                            config = cfg,
                            isAntiBackslideCheck = false
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Test Wake Screen", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        HybridAlarmScheduler.triggerImmediateTestChallenge(
                            context = context,
                            config = cfg,
                            isAntiBackslideCheck = true
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Test 5m Check-In", color = CircadianAmber)
                }
            }
        }

        // Section 1: Wake-Up Verification Challenges
        BiometricCard(
            borderColor = ObsidianBorder,
            backgroundColor = ObsidianCard
        ) {
            Text(
                text = "Wake-Up Verification Challenges",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Guarantees you physically leave bed before alarm audio can be stopped.",
                style = MaterialTheme.typography.bodySmall,
                color = SoftSilver
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 1. 5-Minute Anti-Backslide Check-In
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Delayed 5-Minute Check-In",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Fires a follow-up check-in 5m after waking. Re-alarms if not confirmed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftSilver
                    )
                }
                Switch(
                    checked = cfg.enableFiveMinAntiBackslide,
                    onCheckedChange = { viewModel.updateConfig { c -> c.copy(enableFiveMinAntiBackslide = it) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = AnchorCyan)
                )
            }

            HorizontalDivider(color = ObsidianBorder, modifier = Modifier.padding(vertical = 12.dp))

            // 2. Physical Step Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Physical Step Counter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Requires walking ${cfg.requiredSteps} steps before alarm stops.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftSilver
                    )
                }
                Switch(
                    checked = cfg.enableStepChallenge,
                    onCheckedChange = { viewModel.updateConfig { c -> c.copy(enableStepChallenge = it) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = AnchorCyan)
                )
            }

            if (cfg.enableStepChallenge) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Required Steps: ${cfg.requiredSteps} steps",
                    style = MaterialTheme.typography.labelMedium,
                    color = AnchorCyan
                )
                Slider(
                    value = cfg.requiredSteps.toFloat(),
                    onValueChange = { v ->
                        viewModel.updateConfig { it.copy(requiredSteps = v.toInt().coerceIn(5, 50)) }
                    },
                    valueRange = 5f..50f,
                    colors = SliderDefaults.colors(thumbColor = AnchorCyan, activeTrackColor = AnchorCyan)
                )
            }

            HorizontalDivider(color = ObsidianBorder, modifier = Modifier.padding(vertical = 12.dp))

            // 3. QR / Barcode Scanner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "QR / Barcode Scanner",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Requires scanning a code in bathroom/kitchen to dismiss.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftSilver
                    )
                }
                Switch(
                    checked = cfg.enableBarcodeChallenge,
                    onCheckedChange = { viewModel.updateConfig { c -> c.copy(enableBarcodeChallenge = it) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = AnchorCyan)
                )
            }

            if (cfg.enableBarcodeChallenge) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = barcodeText,
                    onValueChange = { newVal ->
                        barcodeText = newVal
                        viewModel.updateConfig { it.copy(registeredBarcodeValue = newVal.trim().ifEmpty { null }) }
                    },
                    label = { Text("Target Barcode Value (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AnchorCyan,
                        unfocusedBorderColor = ObsidianBorder
                    )
                )
            }
        }

        // Section 2: Timing & Hybrid Alarm Parameters
        BiometricCard(
            borderColor = ObsidianBorder,
            backgroundColor = ObsidianCard
        ) {
            Text(
                text = "Circadian Timing Parameters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Mirror to System Clock App
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Mirror to Android Clock App",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Sets a backup alarm in the system Clock app via ACTION_SET_ALARM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftSilver
                    )
                }
                Switch(
                    checked = cfg.mirrorToSystemClockApp,
                    onCheckedChange = { viewModel.updateConfig { c -> c.copy(mirrorToSystemClockApp = it) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = AnchorCyan)
                )
            }

            HorizontalDivider(color = ObsidianBorder, modifier = Modifier.padding(vertical = 12.dp))

            // Latency Buffer Slider
            Text(
                text = "Sleep Onset Latency: ${cfg.latencyBufferMinutes} min (Default: 14m)",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Slider(
                value = cfg.latencyBufferMinutes.toFloat(),
                onValueChange = { v ->
                    viewModel.updateConfig { it.copy(latencyBufferMinutes = v.toInt().coerceIn(5, 30)) }
                },
                valueRange = 5f..30f,
                colors = SliderDefaults.colors(thumbColor = AnchorCyan, activeTrackColor = AnchorCyan)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { viewModel.updateConfig { com.darkside3211.biphasicsleep.domain.SleepProtocolConfig() } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reset All Parameters to v1.0 Defaults", color = CircadianAmber)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Tucked Simulation & Incident Bottom Sheet Content
 */
@Composable
private fun SimulationBottomSheetContent(
    uiState: ProtocolUiState,
    onSelectPreset: (SimulationPreset) -> Unit,
    onReplayIncident: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Circadian Simulation Engine",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = "Time-travel to any weekly state machine phase or incident.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftSilver
                )
            }
            IconButton(onClick = onReplayIncident) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = "Replay Incident",
                    tint = CircadianAmber
                )
            }
        }

        HorizontalDivider(color = ObsidianBorder)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SimulationPreset.entries.forEach { preset ->
                val isSelected = preset == uiState.activeSimulationPreset
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.5.dp,
                            if (isSelected) AnchorCyan else ObsidianBorderSubtle,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectPreset(preset) },
                    color = if (isSelected) Color(0xFF16253B) else ObsidianCard
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (isSelected) AnchorCyan else TextPrimary
                            )
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftSilver,
                                fontSize = 11.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = AnchorCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable Dialog wrapper for Material 3 TimePicker.
 */
@Composable
private fun BiometricTimePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ObsidianCardElevated,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))
                content()
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SoftSilver)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = AnchorCyan)
                    ) {
                        Text("Set Time", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
