package com.darkside3211.biphasicsleep.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.roundToInt

data class DeviationInput(
    val sleepStartTimestamp: ZonedDateTime,
    val actualWakeTimestamp: ZonedDateTime,
    val triggerReason: String = "Off-schedule sleep or accidental long nap"
)

data class RecoveryStep(
    val stepNumber: Int,
    val windowLabel: String,
    val title: String,
    val description: String,
    val isCriticalGuardrail: Boolean
)

data class DeviationRecoveryPlan(
    val input: DeviationInput,
    val actualSleepDuration: Duration,
    val estimatedCyclesCompleted: Int,
    val targetWakeTimeBaseline: ZonedDateTime,
    val wakeWindowDeltaFromBaseline: Duration,
    val nextAnchorTimestamp: ZonedDateTime,
    val nextAnchorState: ProtocolStateId,
    val timeUntilNextAnchor: Duration,
    val enforceZeroNapLockout: Boolean,
    val capPrecedingOvernightToTwoCycles: Boolean,
    val postOvernightWakeToWorkCoreDuration: Duration?,
    val headline: String,
    val physiologicalAnalysis: List<String>,
    val executedRecoverySteps: List<RecoveryStep>
)

/**
 * Module 5: Deviation Recovery Calculator & Section 3 Incident Log Engine.
 *
 * Calculates the delta between Actual_Wake_Time and Next_Core_Anchor (12:30 or 01:00):
 * - If Time_Until_Next_Anchor >= 16 hours: Enforces a strict Zero-Nap Lockout until the anchor.
 * - If the subsequent 12:30 workday core anchor is within 8–12 hours after an overnight block:
 *   Automatically caps the preceding overnight sleep block to 2 cycles (3h 14m) so sufficient
 *   sleep drive (~8.25h awake) remains for Monday's 12:30 afternoon sleep.
 */
class DeviationRecoveryCalculator(
    private val config: SleepProtocolConfig = SleepProtocolConfig()
) {

    fun evaluateDeviation(input: DeviationInput): DeviationRecoveryPlan {
        val sleepDuration = Duration.between(input.sleepStartTimestamp, input.actualWakeTimestamp)
        val totalSleepMinutes = max(0L, sleepDuration.toMinutes())
        val netCycleMinutes = max(0L, totalSleepMinutes - config.latencyBufferMinutes)
        val estimatedCycles = (netCycleMinutes.toDouble() / config.cycleDurationMinutes.toDouble())
            .roundToInt()
            .coerceAtLeast(1)

        val wakeTime = input.actualWakeTimestamp
        val (nextAnchorTime, nextAnchorState) = resolveNextAnchor(wakeTime, estimatedCycles)
        val timeUntilNextAnchor = Duration.between(wakeTime, nextAnchorTime)

        // Rule 1: If Time_Until_Next_Anchor >= 16 hours, enforce strict Zero-Nap Lockout
        val enforceZeroNapLockout = timeUntilNextAnchor.toMinutes() >= 16 * 60 ||
            nextAnchorState == ProtocolStateId.STATE_WKND_CORE ||
            nextAnchorState == ProtocolStateId.STATE_BRIDGE_TO_WORK

        // Baseline wake comparison (e.g., 05:44 Sunday for STATE_WKND_CORE)
        val baselineWake = resolveExpectedBaselineWake(wakeTime)
        val wakeDelta = Duration.between(wakeTime, baselineWake).abs()

        // Rule 2: Check if the next anchor is an overnight block followed within 8-12h by a 12:30 Work Core
        val nextWorkCoreAfterOvernight = resolveSubsequentWorkCoreAnchor(nextAnchorTime, nextAnchorState)
        val postOvernightWake2Cycle = nextAnchorTime.plusMinutes(config.bridgeBlock2CyclesMinutes.toLong())
        val postOvernightWake3Cycle = nextAnchorTime.plusMinutes(config.coreBlock3CyclesMinutes.toLong())

        val wakeToWorkCoreIf2Cycles = nextWorkCoreAfterOvernight?.let {
            Duration.between(postOvernightWake2Cycle, it)
        }
        val wakeToWorkCoreIf3Cycles = nextWorkCoreAfterOvernight?.let {
            Duration.between(postOvernightWake3Cycle, it)
        }

        // If waking after a 2-cycle overnight block places the 12:30 workday anchor within 8-12 hours
        // (e.g., 04:14 -> 12:30 = 8h 16m), cap the overnight sleep block to 2 cycles (3h 14m).
        val capOvernightToTwoCycles = nextAnchorState == ProtocolStateId.STATE_BRIDGE_TO_WORK ||
            (wakeToWorkCoreIf2Cycles != null &&
                wakeToWorkCoreIf2Cycles.toMinutes() in (8 * 60)..(12 * 60))

        val sleepHours = totalSleepMinutes / 60
        val sleepRemMinutes = totalSleepMinutes % 60
        val wakeStretchHours = timeUntilNextAnchor.toMinutes() / 60
        val wakeStretchRemMinutes = timeUntilNextAnchor.toMinutes() % 60

        val analysis = buildList {
            add(
                "Sleep Summary: Slept from ${formatTime(input.sleepStartTimestamp)} to ${formatTime(wakeTime)} " +
                    "(${sleepHours}h ${sleepRemMinutes}m, ~$estimatedCycles cycles), " +
                    "${formatDuration(wakeDelta)} from the ${formatTime(baselineWake)} scheduled wake time."
            )
            if (estimatedCycles >= 4) {
                add(
                    "Extra Sleep Logged: This $estimatedCycles-cycle sleep cleared built-up fatigue, " +
                        "so you have plenty of energy to stay awake until ${formatTime(nextAnchorTime)}."
                )
            }
            add(
                "Awake Window: Stay awake for ${wakeStretchHours}h ${wakeStretchRemMinutes}m " +
                    "(${formatTime(wakeTime)} to ${formatTime(nextAnchorTime)}) so you can fall asleep on time."
            )
            if (capOvernightToTwoCycles && wakeToWorkCoreIf2Cycles != null && wakeToWorkCoreIf3Cycles != null) {
                add(
                    "Pre-Shift Sleep Guardrail: Keep your ${formatTime(nextAnchorTime)} sleep to 2 cycles " +
                        "(3h 14m -> wake ${formatTime(postOvernightWake2Cycle)}) so you get " +
                        "${formatDuration(wakeToWorkCoreIf2Cycles)} awake before your 12:30 PM workday sleep. " +
                        "Sleeping 3 cycles (waking at ${formatTime(postOvernightWake3Cycle)}) leaves only " +
                        "${formatDuration(wakeToWorkCoreIf3Cycles)}, making it hard to sleep before work."
                )
            }
        }

        val steps = buildRecoverySteps(
            wakeTime = wakeTime,
            nextAnchorTime = nextAnchorTime,
            nextAnchorState = nextAnchorState,
            timeUntilNextAnchor = timeUntilNextAnchor,
            enforceZeroNapLockout = enforceZeroNapLockout,
            capOvernightToTwoCycles = capOvernightToTwoCycles,
            postOvernightWake2Cycle = postOvernightWake2Cycle,
            nextWorkCoreAfterOvernight = nextWorkCoreAfterOvernight
        )

        val headline = if (enforceZeroNapLockout && capOvernightToTwoCycles) {
            "STAY AWAKE (${wakeStretchHours}h ${wakeStretchRemMinutes}m) + CAP OVERNIGHT TO 3h 14m"
        } else if (enforceZeroNapLockout) {
            "STAY AWAKE (NO NAPS) UNTIL ${formatTime(nextAnchorTime)}"
        } else {
            "CAP NEXT SLEEP TO 2 CYCLES (3h 14m)"
        }

        return DeviationRecoveryPlan(
            input = input,
            actualSleepDuration = sleepDuration,
            estimatedCyclesCompleted = estimatedCycles,
            targetWakeTimeBaseline = baselineWake,
            wakeWindowDeltaFromBaseline = wakeDelta,
            nextAnchorTimestamp = nextAnchorTime,
            nextAnchorState = nextAnchorState,
            timeUntilNextAnchor = timeUntilNextAnchor,
            enforceZeroNapLockout = enforceZeroNapLockout,
            capPrecedingOvernightToTwoCycles = capOvernightToTwoCycles,
            postOvernightWakeToWorkCoreDuration = wakeToWorkCoreIf2Cycles,
            headline = headline,
            physiologicalAnalysis = analysis,
            executedRecoverySteps = steps
        )
    }

    private fun resolveNextAnchor(
        wakeTime: ZonedDateTime,
        estimatedCycles: Int
    ): Pair<ZonedDateTime, ProtocolStateId> {
        val day = wakeTime.dayOfWeek
        val localTime = wakeTime.toLocalTime()

        return when (day) {
            DayOfWeek.SATURDAY -> {
                val sunAnchor = wakeTime.toLocalDate().plusDays(1)
                    .atTime(config.wkndCoreStart)
                    .atZone(wakeTime.zone)
                sunAnchor to ProtocolStateId.STATE_WKND_CORE
            }
            DayOfWeek.SUNDAY -> {
                // If waking on Sunday morning (or just before 01:00 after a long Saturday crash),
                // target Monday 01:00 STATE_BRIDGE_TO_WORK
                if (localTime.isBefore(config.wkndCoreStart) && estimatedCycles < 2) {
                    val todayAnchor = wakeTime.toLocalDate()
                        .atTime(config.wkndCoreStart)
                        .atZone(wakeTime.zone)
                    todayAnchor to ProtocolStateId.STATE_WKND_CORE
                } else {
                    val monAnchor = wakeTime.toLocalDate().plusDays(1)
                        .atTime(config.monBridgeStart)
                        .atZone(wakeTime.zone)
                    monAnchor to ProtocolStateId.STATE_BRIDGE_TO_WORK
                }
            }
            DayOfWeek.MONDAY -> {
                if (localTime.isBefore(config.monBridgeStart) && estimatedCycles < 2) {
                    val bridgeAnchor = wakeTime.toLocalDate()
                        .atTime(config.monBridgeStart)
                        .atZone(wakeTime.zone)
                    bridgeAnchor to ProtocolStateId.STATE_BRIDGE_TO_WORK
                } else if (localTime.isBefore(config.workCoreStart)) {
                    val workCore = wakeTime.toLocalDate()
                        .atTime(config.workCoreStart)
                        .atZone(wakeTime.zone)
                    workCore to ProtocolStateId.STATE_WORK_CORE
                } else {
                    val nextDayWorkCore = wakeTime.toLocalDate().plusDays(1)
                        .atTime(config.workCoreStart)
                        .atZone(wakeTime.zone)
                    nextDayWorkCore to ProtocolStateId.STATE_WORK_CORE
                }
            }
            else -> {
                if (localTime.isBefore(config.workCoreStart)) {
                    val todayWorkCore = wakeTime.toLocalDate()
                        .atTime(config.workCoreStart)
                        .atZone(wakeTime.zone)
                    todayWorkCore to ProtocolStateId.STATE_WORK_CORE
                } else {
                    val tomorrow = wakeTime.toLocalDate().plusDays(1)
                    if (tomorrow.dayOfWeek == DayOfWeek.SATURDAY) {
                        val satBridge = tomorrow.atTime(config.satBridgeStart).atZone(wakeTime.zone)
                        satBridge to ProtocolStateId.STATE_BRIDGE_TO_WKND
                    } else {
                        val nextWorkCore = tomorrow.atTime(config.workCoreStart).atZone(wakeTime.zone)
                        nextWorkCore to ProtocolStateId.STATE_WORK_CORE
                    }
                }
            }
        }
    }

    private fun resolveExpectedBaselineWake(wakeTime: ZonedDateTime): ZonedDateTime {
        return when (wakeTime.dayOfWeek) {
            DayOfWeek.SUNDAY -> wakeTime.toLocalDate().atTime(config.wkndCoreEnd).atZone(wakeTime.zone)
            DayOfWeek.SATURDAY -> wakeTime.toLocalDate().atTime(config.satBridgeEnd).atZone(wakeTime.zone)
            DayOfWeek.MONDAY -> {
                if (wakeTime.toLocalTime().isBefore(LocalTime.of(10, 0))) {
                    wakeTime.toLocalDate().atTime(config.monBridgeEnd).atZone(wakeTime.zone)
                } else {
                    wakeTime.toLocalDate().atTime(config.workCoreEnd).atZone(wakeTime.zone)
                }
            }
            else -> wakeTime.toLocalDate().atTime(config.workCoreEnd).atZone(wakeTime.zone)
        }
    }

    private fun resolveSubsequentWorkCoreAnchor(
        nextAnchorTime: ZonedDateTime,
        nextAnchorState: ProtocolStateId
    ): ZonedDateTime? {
        return if (nextAnchorState == ProtocolStateId.STATE_BRIDGE_TO_WORK ||
            (nextAnchorTime.dayOfWeek in listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) &&
                nextAnchorTime.toLocalTime().isBefore(LocalTime.of(6, 0)))
        ) {
            nextAnchorTime.toLocalDate().atTime(config.workCoreStart).atZone(nextAnchorTime.zone)
        } else {
            null
        }
    }

    private fun buildRecoverySteps(
        wakeTime: ZonedDateTime,
        nextAnchorTime: ZonedDateTime,
        nextAnchorState: ProtocolStateId,
        timeUntilNextAnchor: Duration,
        enforceZeroNapLockout: Boolean,
        capOvernightToTwoCycles: Boolean,
        postOvernightWake2Cycle: ZonedDateTime,
        nextWorkCoreAfterOvernight: ZonedDateTime?
    ): List<RecoveryStep> {
        val steps = mutableListOf<RecoveryStep>()

        // Step 1: Current wake until next anchor
        steps.add(
            RecoveryStep(
                stepNumber = 1,
                windowLabel = "${formatDayTime(wakeTime)} – ${formatDayTime(nextAnchorTime)}",
                title = if (enforceZeroNapLockout) {
                    "Stay Awake — Skip Daytime Naps (${formatDuration(timeUntilNextAnchor)})"
                } else {
                    "Stay Awake Until Next Bedtime (${formatDuration(timeUntilNextAnchor)})"
                },
                description = "Skipping naps today makes sure you're tired enough to fall asleep right at ${formatTime(nextAnchorTime)}.",
                isCriticalGuardrail = enforceZeroNapLockout
            )
        )

        if (capOvernightToTwoCycles && nextWorkCoreAfterOvernight != null) {
            val pressureWindow = Duration.between(postOvernightWake2Cycle, nextWorkCoreAfterOvernight)
            val workCoreEnd = nextWorkCoreAfterOvernight.plusMinutes(config.coreBlock3CyclesMinutes.toLong())

            // Step 2: Execute 2-Cycle Bridge
            steps.add(
                RecoveryStep(
                    stepNumber = 2,
                    windowLabel = "${formatDayTime(nextAnchorTime)} – ${formatDayTime(postOvernightWake2Cycle)}",
                    title = "Take Short Reset Sleep (2 Cycles / 3h 14m)",
                    description = "Important: Cap this night sleep at 3h 14m (waking at 04:14 AM). If you sleep 4h 44m and wake at 05:44 AM, you won't be tired enough to sleep at 12:30 PM before Monday's shift.",
                    isCriticalGuardrail = true
                )
            )

            // Step 3: Accumulate sleep pressure (04:14 -> 12:30)
            steps.add(
                RecoveryStep(
                    stepNumber = 3,
                    windowLabel = "${formatDayTime(postOvernightWake2Cycle)} – ${formatDayTime(nextWorkCoreAfterOvernight)}",
                    title = "Stay Awake Monday Morning (${formatDuration(pressureWindow)})",
                    description = "Stay up from ${formatTime(postOvernightWake2Cycle)} to ${formatTime(nextWorkCoreAfterOvernight)} (~8.25h) so you can fall asleep easily for your afternoon sleep.",
                    isCriticalGuardrail = false
                )
            )

            // Step 4: Execute Monday Work Core
            steps.add(
                RecoveryStep(
                    stepNumber = 4,
                    windowLabel = "${formatDayTime(nextWorkCoreAfterOvernight)} – ${formatDayTime(workCoreEnd)}",
                    title = "Afternoon Workday Sleep (3 Cycles / 4h 44m)",
                    description = "Full 4h 44m afternoon sleep (12:30 PM – 5:14 PM) before your 9:00 PM work shift.",
                    isCriticalGuardrail = false
                )
            )
        } else {
            val blockMinutes = if (nextAnchorState == ProtocolStateId.STATE_BRIDGE_TO_WKND) {
                config.bridgeBlock2CyclesMinutes
            } else {
                config.coreBlock3CyclesMinutes
            }
            val blockEnd = nextAnchorTime.plusMinutes(blockMinutes.toLong())
            steps.add(
                RecoveryStep(
                    stepNumber = 2,
                    windowLabel = "${formatDayTime(nextAnchorTime)} – ${formatDayTime(blockEnd)}",
                    title = "${nextAnchorState.title} (${nextAnchorState.durationLabel})",
                    description = nextAnchorState.systemRulesSummary,
                    isCriticalGuardrail = true
                )
            )
        }

        return steps
    }

    companion object {
        /**
         * Reconstructs the exact Section 3 Incident Log test case:
         * Saturday, Oct 17, 2026 @ 20:00 (Accidental Nap Trap) -> Sunday, Oct 18, 2026 @ 04:27 (5-cycle wake).
         */
        fun createOct17To18IncidentInput(zoneId: ZoneId = ZoneId.systemDefault()): DeviationInput {
            val sleepStart = ZonedDateTime.of(
                LocalDate.of(2026, 10, 17),
                LocalTime.of(20, 0),
                zoneId
            )
            val actualWake = ZonedDateTime.of(
                LocalDate.of(2026, 10, 18),
                LocalTime.of(4, 27),
                zoneId
            )
            return DeviationInput(
                sleepStartTimestamp = sleepStart,
                actualWakeTimestamp = actualWake,
                triggerReason = "Oct 17–18 Incident: 20:00 accidental 20m nap converted into 5-cycle sleep (woke 04:27 Sun)"
            )
        }

        fun formatTime(dt: ZonedDateTime): String =
            String.format("%02d:%02d", dt.hour, dt.minute)

        fun formatDayTime(dt: ZonedDateTime): String {
            val dayShort = dt.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            return "${formatTime(dt)} $dayShort"
        }

        fun formatDuration(duration: Duration): String {
            val totalMinutes = duration.toMinutes().coerceAtLeast(0)
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            return "${hours}h ${mins}m"
        }
    }
}
