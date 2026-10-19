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
        subtitle = "Real-time device clock",
        simulatedDateTime = null,
        simulatedLastWakeTime = null
    ),
    WORK_CORE_MON_1230(
        label = "Work Core (Mon 12:30)",
        subtitle = "Launch Window Open (11:30–14:00) • Only 3-Cycle Core (+284m) Unlocked",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(12, 30),
            ZoneId.systemDefault()
        )
    ),
    INCIDENT_2_POST_BRIDGE_COOLDOWN_0430(
        label = "Incident #2: Post-Bridge Cooldown (Mon 04:30)",
        subtitle = "Rule 2 Active: 90m Anti-Backslide Post-Wake Nap Hard-Lock (04:14–05:44)",
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
        label = "Incident #2: Wake Lockout (Mon 06:05)",
        subtitle = "Mandatory 04:14–11:30 Wake Lockout (6h 25m to 12:30) • Alternative Cycles Blocked",
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
        label = "Pre-Anchor Cutoff (Mon 08:30)",
        subtitle = "Rule 1 Active: < 4.5h before 12:30 Core Anchor • 20m Nap Hard-Locked",
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
        label = "Mid-Shift Flush (Tue 01:15)",
        subtitle = "Mid-Shift 20m Adenosine Flush Unlocked • Caffeine Gate at 02:00",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 20),
            LocalTime.of(1, 15),
            ZoneId.systemDefault()
        )
    ),
    SUNGLASSES_COMMUTE_0730(
        label = "Post-Shift Commute (Wed 07:30)",
        subtitle = "06:00–09:00 Dark Sunglasses Daylight Blunting Before 12:30 Sleep",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 21),
            LocalTime.of(7, 30),
            ZoneId.systemDefault()
        )
    ),
    SAT_BRIDGE_0800(
        label = "Weekend Bridge #1 (Sat 08:00)",
        subtitle = "Launch Window Open (06:30–09:30) • Only 2-Cycle Bridge (+194m) Unlocked",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 17),
            LocalTime.of(8, 0),
            ZoneId.systemDefault()
        )
    ),
    SAT_BOREDOM_LOCKOUT_2000(
        label = "Incident #1: Boredom Danger Zone (Sat 20:00)",
        subtitle = "Rule 3 Active: 18:00–01:00 Nap Hard-Lock Before 01:00 Anchor",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 17),
            LocalTime.of(20, 0),
            ZoneId.systemDefault()
        )
    ),
    OCT_18_INCIDENT_WAKE_0427(
        label = "Oct 18 Incident Wake (Sun 04:27)",
        subtitle = "Woke After 5-Cycle Accidental Nap -> 20h 33m Zero-Nap Lockout",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(4, 27),
            ZoneId.systemDefault()
        )
    ),
    MON_BRIDGE_0100(
        label = "Pre-Work Bridge #2 (Mon 01:00)",
        subtitle = "Launch Window Open (00:00–02:30) • Only 2-Cycle Bridge (+194m) Unlocked",
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
            phaseEvaluation = initialMachine.evaluate(initialNow, null, null, null),
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
                if (wakeMillis != null) {
                    val wakeDt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wakeMillis), ZoneId.systemDefault())
                    recordWakeEvent(wakeDt)
                }
            }
        }
    }

    fun refreshEvaluation() {
        _uiState.update { current ->
            val effectiveClock = current.activeSimulationPreset.simulatedDateTime ?: ZonedDateTime.now()
            val effectiveLastWake = current.activeSimulationPreset.simulatedLastWakeTime ?: current.lastWakeTimestamp
            val machine = CircadianStateMachine(current.config)
            val calculator = DeviationRecoveryCalculator(current.config)
            val plan = calculator.evaluateDeviation(current.deviationInput)
            val appliedOverride = if (current.isDeviationOverrideAppliedToDeck) plan else null
            current.copy(
                currentClock = effectiveClock,
                computedDeviationPlan = plan,
                phaseEvaluation = machine.evaluate(
                    now = effectiveClock,
                    activeDeviation = appliedOverride,
                    lastActualWakeTime = effectiveLastWake,
                    completedAnchorAt = current.completedAnchorTimestamp
                )
            )
        }
    }

    fun selectSimulationPreset(preset: SimulationPreset, applyIncidentOverride: Boolean = false) {
        _uiState.update { current ->
            val effectiveClock = preset.simulatedDateTime ?: ZonedDateTime.now()
            val effectiveLastWake = preset.simulatedLastWakeTime ?: current.lastWakeTimestamp
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
                isDeviationOverrideAppliedToDeck = shouldApplyOverride,
                phaseEvaluation = machine.evaluate(
                    now = effectiveClock,
                    activeDeviation = activeOverride,
                    lastActualWakeTime = effectiveLastWake,
                    completedAnchorAt = current.completedAnchorTimestamp
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
                deviationInput = incidentInput,
                computedDeviationPlan = plan,
                isDeviationOverrideAppliedToDeck = true,
                phaseEvaluation = machine.evaluate(
                    now = effectiveClock,
                    activeDeviation = plan,
                    lastActualWakeTime = incidentInput.actualWakeTimestamp,
                    completedAnchorAt = null
                ),
                lastStatusToast = "Oct 17–18 Incident Recovery Applied (20h 33m Lockout)"
            )
        }
    }

    fun loadIncident2Replay() {
        selectSimulationPreset(SimulationPreset.INCIDENT_2_WAKE_LOCKOUT_0605)
    }

    fun recordWakeEvent(wakeDateTime: ZonedDateTime) {
        _uiState.update { current ->
            val machine = CircadianStateMachine(current.config)
            val appliedOverride = if (current.isDeviationOverrideAppliedToDeck) current.computedDeviationPlan else null
            current.copy(
                lastWakeTimestamp = wakeDateTime,
                phaseEvaluation = machine.evaluate(
                    now = current.currentClock,
                    activeDeviation = appliedOverride,
                    lastActualWakeTime = wakeDateTime,
                    completedAnchorAt = current.completedAnchorTimestamp
                ),
                lastStatusToast = "Wake logged at ${DeviationRecoveryCalculator.formatTime(wakeDateTime)} • 90m Cooldown Started"
            )
        }
    }

    fun recordAnchorCompleted(completedAt: ZonedDateTime) {
        _uiState.update { current ->
            val machine = CircadianStateMachine(current.config)
            val appliedOverride = if (current.isDeviationOverrideAppliedToDeck) current.computedDeviationPlan else null
            current.copy(
                completedAnchorTimestamp = completedAt,
                phaseEvaluation = machine.evaluate(
                    now = current.currentClock,
                    activeDeviation = appliedOverride,
                    lastActualWakeTime = current.lastWakeTimestamp,
                    completedAnchorAt = completedAt
                ),
                lastStatusToast = "Anchor completed • Transitioned to STATE_WAKE_LOCKOUT"
            )
        }
    }

    fun toggleApplyDeviationToDeck(enabled: Boolean) {
        _uiState.update { current ->
            val machine = CircadianStateMachine(current.config)
            val override = if (enabled) current.computedDeviationPlan else null
            val effectiveLastWake = current.activeSimulationPreset.simulatedLastWakeTime ?: current.lastWakeTimestamp
            current.copy(
                isDeviationOverrideAppliedToDeck = enabled,
                phaseEvaluation = machine.evaluate(
                    now = current.currentClock,
                    activeDeviation = override,
                    lastActualWakeTime = effectiveLastWake,
                    completedAnchorAt = current.completedAnchorTimestamp
                ),
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
            updated.copy(lastWakeTimestamp = now)
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
        val calculator = DeviationRecoveryCalculator(current.config)
        val newPlan = calculator.evaluateDeviation(newInput)
        val machine = CircadianStateMachine(current.config)
        val activeOverride = if (current.isDeviationOverrideAppliedToDeck) newPlan else null
        val effectiveLastWake = current.activeSimulationPreset.simulatedLastWakeTime ?: current.lastWakeTimestamp

        return current.copy(
            deviationInput = newInput,
            computedDeviationPlan = newPlan,
            phaseEvaluation = machine.evaluate(
                now = current.currentClock,
                activeDeviation = activeOverride,
                lastActualWakeTime = effectiveLastWake,
                completedAnchorAt = current.completedAnchorTimestamp
            )
        )
    }

    fun updateConfig(transform: (SleepProtocolConfig) -> SleepProtocolConfig) {
        _uiState.update { current ->
            val newConfig = transform(current.config)
            val machine = CircadianStateMachine(newConfig)
            val calculator = DeviationRecoveryCalculator(newConfig)
            val newPlan = calculator.evaluateDeviation(current.deviationInput)
            val activeOverride = if (current.isDeviationOverrideAppliedToDeck) newPlan else null
            val effectiveLastWake = current.activeSimulationPreset.simulatedLastWakeTime ?: current.lastWakeTimestamp
            current.copy(
                config = newConfig,
                computedDeviationPlan = newPlan,
                phaseEvaluation = machine.evaluate(
                    now = current.currentClock,
                    activeDeviation = activeOverride,
                    lastActualWakeTime = effectiveLastWake,
                    completedAnchorAt = current.completedAnchorTimestamp
                )
            )
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
