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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SimulationPreset(
    val label: String,
    val subtitle: String,
    val simulatedDateTime: ZonedDateTime?
) {
    LIVE_SYSTEM_CLOCK(
        label = "Live System Clock",
        subtitle = "Real-time device clock",
        simulatedDateTime = null
    ),
    WORK_CORE_MON_1230(
        label = "Work Core (Mon 12:30)",
        subtitle = "Primary 3-Cycle Workday Anchor (4h 44m)",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(12, 30),
            ZoneId.systemDefault()
        )
    ),
    WORK_NAP_TUE_0115(
        label = "Mid-Shift Flush (Tue 01:15)",
        subtitle = "Mid-Shift 20m Adenosine Flush + Caffeine Gate",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 20),
            LocalTime.of(1, 15),
            ZoneId.systemDefault()
        )
    ),
    SUNGLASSES_COMMUTE_0730(
        label = "Post-Shift Commute (Wed 07:30)",
        subtitle = "06:00–09:00 Dark Sunglasses Daylight Blunting",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 21),
            LocalTime.of(7, 30),
            ZoneId.systemDefault()
        )
    ),
    SAT_BRIDGE_0800(
        label = "Weekend Bridge #1 (Sat 08:00)",
        subtitle = "Post-Shift 2-Cycle Bridge #1 (3h 14m cap)",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 17),
            LocalTime.of(8, 0),
            ZoneId.systemDefault()
        )
    ),
    SAT_BOREDOM_LOCKOUT_2000(
        label = "Boredom Danger Zone (Sat 20:00)",
        subtitle = "18:00–23:00 Anti-Nap Lockout before 01:00 Anchor",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 17),
            LocalTime.of(20, 0),
            ZoneId.systemDefault()
        )
    ),
    OCT_18_INCIDENT_WAKE_0427(
        label = "Oct 18 Incident Wake (Sun 04:27)",
        subtitle = "Woke after 5-cycle accidental nap -> 20h 33m Zero-Nap Lockout",
        simulatedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(4, 27),
            ZoneId.systemDefault()
        )
    ),
    MON_BRIDGE_0100(
        label = "Pre-Work Bridge #2 (Mon 01:00)",
        subtitle = "Locked to 2-Cycle Bridge (3h 14m -> 04:14 wake)",
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
            phaseEvaluation = initialMachine.evaluate(initialNow, null),
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
    }

    fun refreshEvaluation() {
        _uiState.update { current ->
            val effectiveClock = current.activeSimulationPreset.simulatedDateTime ?: ZonedDateTime.now()
            val machine = CircadianStateMachine(current.config)
            val calculator = DeviationRecoveryCalculator(current.config)
            val plan = calculator.evaluateDeviation(current.deviationInput)
            val appliedOverride = if (current.isDeviationOverrideAppliedToDeck) plan else null
            current.copy(
                currentClock = effectiveClock,
                computedDeviationPlan = plan,
                phaseEvaluation = machine.evaluate(effectiveClock, appliedOverride)
            )
        }
    }

    fun selectSimulationPreset(preset: SimulationPreset, applyIncidentOverride: Boolean = false) {
        _uiState.update { current ->
            val effectiveClock = preset.simulatedDateTime ?: ZonedDateTime.now()
            val machine = CircadianStateMachine(current.config)
            val calculator = DeviationRecoveryCalculator(current.config)
            val shouldApplyOverride = applyIncidentOverride ||
                preset == SimulationPreset.OCT_18_INCIDENT_WAKE_0427
            val plan = calculator.evaluateDeviation(current.deviationInput)
            val activeOverride = if (shouldApplyOverride) plan else null

            current.copy(
                activeSimulationPreset = preset,
                currentClock = effectiveClock,
                isDeviationOverrideAppliedToDeck = shouldApplyOverride,
                phaseEvaluation = machine.evaluate(effectiveClock, activeOverride),
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
                deviationInput = incidentInput,
                computedDeviationPlan = plan,
                isDeviationOverrideAppliedToDeck = true,
                phaseEvaluation = machine.evaluate(effectiveClock, plan),
                lastStatusToast = "Oct 17–18 Incident Recovery Applied (20h 33m Lockout)"
            )
        }
    }

    fun toggleApplyDeviationToDeck(enabled: Boolean) {
        _uiState.update { current ->
            val machine = CircadianStateMachine(current.config)
            val override = if (enabled) current.computedDeviationPlan else null
            current.copy(
                isDeviationOverrideAppliedToDeck = enabled,
                phaseEvaluation = machine.evaluate(current.currentClock, override),
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
                // If start is set after wake, roll start to previous day
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
                // If wake is before start, advance wake to next day
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
            // Default onset to 4h 44m prior if onset was after now
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
            updateDeviationInputInternal(current, newInput)
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

        return current.copy(
            deviationInput = newInput,
            computedDeviationPlan = newPlan,
            phaseEvaluation = machine.evaluate(current.currentClock, activeOverride)
        )
    }

    fun updateConfig(transform: (SleepProtocolConfig) -> SleepProtocolConfig) {
        _uiState.update { current ->
            val newConfig = transform(current.config)
            val machine = CircadianStateMachine(newConfig)
            val calculator = DeviationRecoveryCalculator(newConfig)
            val newPlan = calculator.evaluateDeviation(current.deviationInput)
            val activeOverride = if (current.isDeviationOverrideAppliedToDeck) newPlan else null
            current.copy(
                config = newConfig,
                computedDeviationPlan = newPlan,
                phaseEvaluation = machine.evaluate(current.currentClock, activeOverride)
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
