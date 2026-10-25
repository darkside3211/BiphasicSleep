package com.darkside3211.biphasicsleep.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkside3211.biphasicsleep.alarm.HybridAlarmScheduler
import com.darkside3211.biphasicsleep.domain.CircadianStateMachine
import com.darkside3211.biphasicsleep.domain.DeviationInput
import com.darkside3211.biphasicsleep.domain.DeviationRecoveryCalculator
import com.darkside3211.biphasicsleep.domain.DeviationRecoveryPlan
import com.darkside3211.biphasicsleep.domain.PhaseEvaluation
import com.darkside3211.biphasicsleep.domain.SleepBlockType
import com.darkside3211.biphasicsleep.domain.SleepProtocolConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SimulationPreset(
    val label: String,
    val subtitle: String,
    val simulatedDateTime: ZonedDateTime?,
    val simulatedLastWakeTime: ZonedDateTime? = null
) {
    LIVE_SYSTEM_CLOCK(
        label = "Live System Clock",
        subtitle = "Real-time phone clock",
        simulatedDateTime = null,
        simulatedLastWakeTime = null
    ),
    WORK_CORE_MON_1230(
        label = "Workday Afternoon Sleep (Mon 12:30)",
        subtitle = "On-Time Sleep Window (11:30–14:00) • 4h 44m Full Sleep Recommended",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(12, 30),
            ZoneId.systemDefault()
        )
    ),
    INCIDENT_2_POST_BRIDGE_COOLDOWN_0430(
        label = "Just Woke Up Cooldown (Mon 04:30)",
        subtitle = "Confirmed Wake at 04:14 • 90m Post-Wake Cooldown Active (04:14–05:44)",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(4, 30),
            ZoneId.systemDefault()
        ),
        simulatedLastWakeTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(4, 14),
            ZoneId.systemDefault()
        )
    ),
    INCIDENT_2_WAKE_LOCKOUT_0605(
        label = "Morning Awake Window (Mon 06:05)",
        subtitle = "6h 25m until 12:30 PM Sleep • 20m Power Nap Eligible",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(6, 5),
            ZoneId.systemDefault()
        ),
        simulatedLastWakeTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(4, 14),
            ZoneId.systemDefault()
        )
    ),
    PRE_ANCHOR_CUTOFF_MON_0830(
        label = "Too Close to Bedtime (Mon 08:30)",
        subtitle = "< 4h post-nap before 12:30 PM Sleep • 20m Nap Locked to Protect Sleep Drive",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(8, 30),
            ZoneId.systemDefault()
        ),
        simulatedLastWakeTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(4, 14),
            ZoneId.systemDefault()
        )
    ),
    WORK_NAP_TUE_0115(
        label = "Night Shift 20m Nap (Tue 01:15)",
        subtitle = "Sliding Shift Break Nap Unlocked • Wait Until After Nap for Caffeine",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 20),
            LocalTime.of(1, 15),
            ZoneId.systemDefault()
        )
    ),
    SUNGLASSES_COMMUTE_0730(
        label = "Morning Drive Home (Wed 07:30)",
        subtitle = "06:00–09:00 Wear Dark Sunglasses Before Afternoon Sleep",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 21),
            LocalTime.of(7, 30),
            ZoneId.systemDefault()
        )
    ),
    SAT_BRIDGE_0800(
        label = "Saturday Morning Sleep (Sat 08:00)",
        subtitle = "Post-Shift Window Open (06:30–09:30) • 3h 14m Short Sleep Recommended",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 17),
            LocalTime.of(8, 0),
            ZoneId.systemDefault()
        )
    ),
    SAT_BOREDOM_LOCKOUT_2000(
        label = "Weekend Evening Danger Zone (Sat 20:00)",
        subtitle = "18:00–01:00 Evening Nap Lockout Before 01:00 AM Bedtime",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 17),
            LocalTime.of(20, 0),
            ZoneId.systemDefault()
        )
    ),
    OCT_18_INCIDENT_WAKE_0427(
        label = "Sunday Recovery After 10h Crash (Sun 04:27)",
        subtitle = "Woke After 5-Cycle Crash -> Stay Awake Until 01:00 AM Monday",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(4, 27),
            ZoneId.systemDefault()
        )
    ),
    MON_BRIDGE_0100(
        label = "Sunday Night Reset Sleep (Mon 01:00)",
        subtitle = "Overnight Window Open (00:00–02:30) • 3h 14m Short Sleep Before Monday",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(1, 0),
            ZoneId.systemDefault()
        )
    )
}

data class ProtocolUiState(
    val config: SleepProtocolConfig = SleepProtocolConfig(),
    val activeSimulationPreset: SimulationPreset = SimulationPreset.LIVE_SYSTEM_CLOCK,
    val currentClock: ZonedDateTime = ZonedDateTime.now(),
    val lastWakeTimestamp: ZonedDateTime? = null,
    val completedAnchorTimestamp: ZonedDateTime? = null,
    val completedNapTimestamp: ZonedDateTime? = null,
    val selectedCustomCycleBlock: SleepBlockType? = null,
    val phaseEvaluation: PhaseEvaluation,
    val deviationInput: DeviationInput,
    val computedDeviationPlan: DeviationRecoveryPlan,
    val isDeviationOverrideAppliedToDeck: Boolean = false,
    val lastStatusToast: String? = null
)

class BiphasicProtocolViewModel : ViewModel() {

    private val initialConfig = SleepProtocolConfig()
    private val initialMachine = CircadianStateMachine(initialConfig)
    private val initialCalculator = DeviationRecoveryCalculator(initialConfig)
    private val initialIncidentInput = DeviationRecoveryCalculator.createOct17To18IncidentInput()
    private val initialIncidentPlan = initialCalculator.evaluateDeviation(initialIncidentInput)
    private val initialNow = ZonedDateTime.now()

    private val _uiState = MutableStateFlow(
        ProtocolUiState(
            config = initialConfig,
            activeSimulationPreset = SimulationPreset.LIVE_SYSTEM_CLOCK,
            currentClock = initialNow,
            lastWakeTimestamp = null,
            completedAnchorTimestamp = null,
            completedNapTimestamp = null,
            selectedCustomCycleBlock = null,
            phaseEvaluation = initialMachine.evaluate(initialNow, null, null, null, null),
            deviationInput = initialIncidentInput,
            computedDeviationPlan = initialIncidentPlan,
            isDeviationOverrideAppliedToDeck = false
        )
    )
    val uiState: StateFlow<ProtocolUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(15_000L)
                refreshEvaluation()
            }
        }

        viewModelScope.launch {
            HybridAlarmScheduler.lastVerifiedWakeMillis.collect { wakeMillis ->
                val wakeDt = wakeMillis?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
                _uiState.update { current ->
                    recomputeState(current.copy(lastWakeTimestamp = wakeDt))
                }
            }
        }

        viewModelScope.launch {
            HybridAlarmScheduler.lastCompletedAnchorMillis.collect { anchorMillis ->
                val anchorDt = anchorMillis?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
                _uiState.update { current ->
                    recomputeState(current.copy(completedAnchorTimestamp = anchorDt, lastWakeTimestamp = anchorDt ?: current.lastWakeTimestamp))
                }
            }
        }

        viewModelScope.launch {
            HybridAlarmScheduler.lastCompletedNapMillis.collect { napMillis ->
                val napDt = napMillis?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
                _uiState.update { current ->
                    recomputeState(current.copy(completedNapTimestamp = napDt, lastWakeTimestamp = napDt ?: current.lastWakeTimestamp))
                }
            }
        }
    }

    private fun recomputeState(current: ProtocolUiState): ProtocolUiState {
        val effectiveClock = current.activeSimulationPreset.simulatedDateTime ?: ZonedDateTime.now()
        val effectiveLastWake = current.activeSimulationPreset.simulatedLastWakeTime ?: current.lastWakeTimestamp
        val effectiveCompletedAnchor = current.activeSimulationPreset.simulatedLastWakeTime ?: current.completedAnchorTimestamp
        val machine = CircadianStateMachine(current.config)
        val calculator = DeviationRecoveryCalculator(current.config)
        val plan = calculator.evaluateDeviation(current.deviationInput)
        val appliedOverride = if (current.isDeviationOverrideAppliedToDeck) plan else null
        return current.copy(
            currentClock = effectiveClock,
            computedDeviationPlan = plan,
            phaseEvaluation = machine.evaluate(
                now = effectiveClock,
                activeDeviation = appliedOverride,
                lastActualWakeTime = effectiveLastWake,
                completedAnchorAt = effectiveCompletedAnchor,
                completedNapAt = current.completedNapTimestamp
            )
        )
    }

    fun refreshEvaluation() {
        _uiState.update { current -> recomputeState(current) }
    }

    fun selectCustomCycleBlock(blockType: SleepBlockType?) {
        _uiState.update { current ->
            current.copy(selectedCustomCycleBlock = blockType)
        }
    }

    fun selectSimulationPreset(preset: SimulationPreset, applyIncidentOverride: Boolean = false) {
        _uiState.update { current ->
            val effectiveClock = preset.simulatedDateTime ?: ZonedDateTime.now()
            val effectiveLastWake = preset.simulatedLastWakeTime
            val machine = CircadianStateMachine(current.config)
            val calculator = DeviationRecoveryCalculator(current.config)
            val shouldApplyOverride = applyIncidentOverride ||
                preset == SimulationPreset.OCT_18_INCIDENT_WAKE_0427
            val plan = calculator.evaluateDeviation(current.deviationInput)
            val activeOverride = if (shouldApplyOverride) plan else null

            current.copy(
                activeSimulationPreset = preset,
                currentClock = effectiveClock,
                lastWakeTimestamp = effectiveLastWake,
                completedAnchorTimestamp = effectiveLastWake,
                completedNapTimestamp = null,
                selectedCustomCycleBlock = null,
                isDeviationOverrideAppliedToDeck = shouldApplyOverride,
                phaseEvaluation = machine.evaluate(
                    now = effectiveClock,
                    activeDeviation = activeOverride,
                    lastActualWakeTime = effectiveLastWake,
                    completedAnchorAt = effectiveLastWake,
                    completedNapAt = null
                ),
                lastStatusToast = "Clock set to: ${preset.label}"
            )
        }
    }

    fun loadOct17To18IncidentReplay() {
        val incidentInput = DeviationRecoveryCalculator.createOct17To18IncidentInput()
        _uiState.update { current ->
            val calculator = DeviationRecoveryCalculator(current.config)
            val plan = calculator.evaluateDeviation(incidentInput)
            val preset = SimulationPreset.OCT_18_INCIDENT_WAKE_0427
            val effectiveClock = preset.simulatedDateTime ?: ZonedDateTime.now()
            val machine = CircadianStateMachine(current.config)

            current.copy(
                activeSimulationPreset = preset,
                currentClock = effectiveClock,
                lastWakeTimestamp = incidentInput.actualWakeTimestamp,
                completedAnchorTimestamp = incidentInput.actualWakeTimestamp,
                deviationInput = incidentInput,
                computedDeviationPlan = plan,
                isDeviationOverrideAppliedToDeck = true,
                phaseEvaluation = machine.evaluate(
                    now = effectiveClock,
                    activeDeviation = plan,
                    lastActualWakeTime = incidentInput.actualWakeTimestamp,
                    completedAnchorAt = incidentInput.actualWakeTimestamp,
                    completedNapAt = null
                ),
                lastStatusToast = "Oct 17–18 Incident Recovery Applied (20h 33m Stay-Awake Plan)"
            )
        }
    }

    fun loadIncident2Replay() {
        selectSimulationPreset(SimulationPreset.INCIDENT_2_WAKE_LOCKOUT_0605)
    }

    fun recordWakeEvent(wakeDateTime: ZonedDateTime) {
        _uiState.update { current ->
            val updated = recomputeState(
                current.copy(
                    lastWakeTimestamp = wakeDateTime,
                    completedAnchorTimestamp = wakeDateTime
                )
            )
            updated.copy(
                lastStatusToast = "Wake logged at ${DeviationRecoveryCalculator.formatTime(wakeDateTime)} • 90m Cooldown Started"
            )
        }
    }

    fun recordAnchorCompleted(completedAt: ZonedDateTime, context: Context? = null) {
        HybridAlarmScheduler.recordSessionCompletion(
            context = context,
            blockType = SleepBlockType.CORE_3_CYCLES,
            completedAtMillis = completedAt.toInstant().toEpochMilli()
        )
        _uiState.update { current ->
            val updated = recomputeState(
                current.copy(
                    completedAnchorTimestamp = completedAt,
                    lastWakeTimestamp = completedAt,
                    selectedCustomCycleBlock = null
                )
            )
            updated.copy(
                lastStatusToast = "Sleep marked complete at ${DeviationRecoveryCalculator.formatTime(completedAt)} • 90m Cooldown Active"
            )
        }
    }

    fun recordNapCompleted(completedAt: ZonedDateTime, context: Context? = null) {
        HybridAlarmScheduler.recordSessionCompletion(
            context = context,
            blockType = SleepBlockType.FLUSH_NAP_20M,
            completedAtMillis = completedAt.toInstant().toEpochMilli()
        )
        _uiState.update { current ->
            val updated = recomputeState(
                current.copy(
                    completedNapTimestamp = completedAt,
                    lastWakeTimestamp = completedAt
                )
            )
            updated.copy(
                lastStatusToast = "20m Nap marked complete at ${DeviationRecoveryCalculator.formatTime(completedAt)}"
            )
        }
    }

    fun clearCompletedSessionHistory(context: Context? = null) {
        HybridAlarmScheduler.clearPersistedSessionState(context)
        _uiState.update { current ->
            val clearedPreset = if (current.activeSimulationPreset.simulatedLastWakeTime != null) {
                current.activeSimulationPreset
            } else {
                current.activeSimulationPreset
            }
            val machine = CircadianStateMachine(current.config)
            val appliedOverride = if (current.isDeviationOverrideAppliedToDeck) current.computedDeviationPlan else null
            current.copy(
                activeSimulationPreset = clearedPreset,
                lastWakeTimestamp = null,
                completedAnchorTimestamp = null,
                completedNapTimestamp = null,
                selectedCustomCycleBlock = null,
                phaseEvaluation = machine.evaluate(
                    now = current.currentClock,
                    activeDeviation = appliedOverride,
                    lastActualWakeTime = null,
                    completedAnchorAt = null,
                    completedNapAt = null
                ),
                lastStatusToast = "Cleared logged sleep/nap state • Sleep options unlocked"
            )
        }
    }

    fun toggleApplyDeviationToDeck(enabled: Boolean) {
        _uiState.update { current ->
            val updated = recomputeState(current.copy(isDeviationOverrideAppliedToDeck = enabled))
            updated.copy(
                lastStatusToast = if (enabled) {
                    "Recovery Override active on Sleep screen"
                } else {
                    "Returned to standard schedule"
                }
            )
        }
    }

    fun setDeviationSleepStartTime(hour: Int, minute: Int) {
        _uiState.update { current ->
            val date = current.deviationInput.sleepStartTimestamp.toLocalDate()
            val newStart = ZonedDateTime.of(date, LocalTime.of(hour, minute), current.deviationInput.sleepStartTimestamp.zone)
            if (newStart.isAfter(current.deviationInput.actualWakeTimestamp)) {
                val adjustedStart = newStart.minusDays(1)
                updateDeviationInputInternal(current, current.deviationInput.copy(sleepStartTimestamp = adjustedStart))
            } else {
                updateDeviationInputInternal(current, current.deviationInput.copy(sleepStartTimestamp = newStart))
            }
        }
    }

    fun setDeviationWakeTime(hour: Int, minute: Int) {
        _uiState.update { current ->
            val date = current.deviationInput.actualWakeTimestamp.toLocalDate()
            val newWake = ZonedDateTime.of(date, LocalTime.of(hour, minute), current.deviationInput.actualWakeTimestamp.zone)
            if (newWake.isBefore(current.deviationInput.sleepStartTimestamp)) {
                val adjustedWake = newWake.plusDays(1)
                updateDeviationInputInternal(current, current.deviationInput.copy(actualWakeTimestamp = adjustedWake))
            } else {
                updateDeviationInputInternal(current, current.deviationInput.copy(actualWakeTimestamp = newWake))
            }
        }
    }

    fun setDeviationWakeToNow() {
        _uiState.update { current ->
            val now = current.currentClock
            val start = if (current.deviationInput.sleepStartTimestamp.isBefore(now)) {
                current.deviationInput.sleepStartTimestamp
            } else {
                now.minusHours(4).minusMinutes(44)
            }
            val newInput = current.deviationInput.copy(
                sleepStartTimestamp = start,
                actualWakeTimestamp = now,
                triggerReason = "Logged Real-Time Wake Event"
            )
            val updated = updateDeviationInputInternal(current, newInput)
            updated.copy(
                lastWakeTimestamp = now,
                completedAnchorTimestamp = now,
                lastStatusToast = "Logged wake-up at ${DeviationRecoveryCalculator.formatTime(now)}"
            )
        }
    }

    fun adjustDeviationWakeTimeMinutes(deltaMinutes: Long) {
        _uiState.update { current ->
            val updatedWake = current.deviationInput.actualWakeTimestamp.plusMinutes(deltaMinutes)
            if (!updatedWake.isAfter(current.deviationInput.sleepStartTimestamp.plusMinutes(15))) {
                return@update current
            }
            val newInput = current.deviationInput.copy(
                actualWakeTimestamp = updatedWake,
                triggerReason = "Adjusted Wake Time"
            )
            updateDeviationInputInternal(current, newInput)
        }
    }

    fun adjustDeviationSleepStartMinutes(deltaMinutes: Long) {
        _uiState.update { current ->
            val updatedStart = current.deviationInput.sleepStartTimestamp.plusMinutes(deltaMinutes)
            if (!current.deviationInput.actualWakeTimestamp.isAfter(updatedStart.plusMinutes(15))) {
                return@update current
            }
            val newInput = current.deviationInput.copy(
                sleepStartTimestamp = updatedStart,
                triggerReason = "Adjusted Sleep Start"
            )
            updateDeviationInputInternal(current, newInput)
        }
    }

    private fun updateDeviationInputInternal(
        current: ProtocolUiState,
        newInput: DeviationInput
    ): ProtocolUiState {
        return recomputeState(current.copy(deviationInput = newInput))
    }

    fun updateConfig(transform: (SleepProtocolConfig) -> SleepProtocolConfig) {
        _uiState.update { current ->
            val newConfig = transform(current.config)
            recomputeState(current.copy(config = newConfig))
        }
    }

    fun armSleepBlockAlarm(
        context: Context,
        blockType: SleepBlockType,
        forceSystemClockIntent: Boolean = false
    ) {
        val state = _uiState.value
        val nowMillis = state.currentClock.toInstant().toEpochMilli()
        val armed = HybridAlarmScheduler.scheduleSleepBlockAlarm(
            context = context,
            blockType = blockType,
            stateId = state.phaseEvaluation.activeOrUpcomingState,
            config = state.config,
            nowMillis = nowMillis,
            alsoSetSystemClockIntent = forceSystemClockIntent || state.config.mirrorToSystemClockApp
        )
        val wakeStr = DeviationRecoveryCalculator.formatTime(armed.targetWakeDateTime)
        _uiState.update {
            it.copy(
                lastStatusToast = "Armed ${blockType.shortTitle} -> Wake at $wakeStr (+${armed.durationMinutes}m)"
            )
        }
    }

    fun clearStatusToast() {
        _uiState.update { it.copy(lastStatusToast = null) }
    }
}
