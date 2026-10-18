package com.darkside3211.biphasicsleep.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Module 1, 2 & 3: Weekly State Machine Engine, State-Aware Mode Guardrails,
 * and Weekend Anti-Nap / Boredom Lockout Timer.
 */
class CircadianStateMachine(
    private val config: SleepProtocolConfig = SleepProtocolConfig()
) {

    fun evaluate(
        now: ZonedDateTime,
        activeDeviation: DeviationRecoveryPlan? = null
    ): PhaseEvaluation {
        val state = determineState(now)
        val isInsideShift = isWithinShiftWindow(now)
        val lockoutStatus = evaluateWeekendLockout(now, state, activeDeviation)
        val primaryBlock = determinePrimaryBlock(state, lockoutStatus, activeDeviation)
        val actionOptions = buildActionOptions(now, state, lockoutStatus, activeDeviation)
        val advisories = buildAdvisories(now, state, isInsideShift, lockoutStatus, activeDeviation)
        val (headline, subheadline) = buildPhaseHeadlines(now, state, isInsideShift, lockoutStatus, activeDeviation)

        return PhaseEvaluation(
            evaluatedAt = now,
            activeOrUpcomingState = state,
            phaseStatusHeadline = headline,
            phaseSubheadline = subheadline,
            isInsideShiftWindow = isInsideShift,
            primaryRecommendedBlock = primaryBlock,
            actionOptions = actionOptions,
            weekendLockoutStatus = lockoutStatus,
            advisories = advisories,
            activeDeviationOverride = activeDeviation
        )
    }

    /**
     * Maps any day/time of the week to the governing ProtocolStateId in Section 2.
     */
    fun determineState(now: ZonedDateTime): ProtocolStateId {
        val day = now.dayOfWeek
        val time = now.toLocalTime()

        return when (day) {
            DayOfWeek.SATURDAY -> {
                when {
                    // Saturday 00:00 - 05:59 is Friday night's shift -> mid-shift adenosine flush
                    time.isBefore(config.shiftEnd) -> ProtocolStateId.STATE_WORK_NAP
                    // Saturday 06:00 - 14:00 is Post-Shift 2-Cycle Bridge #1 (~08:00 - ~11:14)
                    time.isBefore(LocalTime.of(14, 0)) -> ProtocolStateId.STATE_BRIDGE_TO_WKND
                    // Saturday afternoon/evening leads into Sunday 01:00 Weekend Core
                    else -> ProtocolStateId.STATE_WKND_CORE
                }
            }
            DayOfWeek.SUNDAY -> {
                when {
                    // Sunday 00:00 - 12:00 covers STATE_WKND_CORE (01:00 - 05:44) & morning wake
                    time.isBefore(LocalTime.of(12, 0)) -> ProtocolStateId.STATE_WKND_CORE
                    // Sunday afternoon/evening builds sleep pressure for Monday 01:00 Bridge #2
                    else -> ProtocolStateId.STATE_BRIDGE_TO_WORK
                }
            }
            DayOfWeek.MONDAY -> {
                when {
                    // Monday 00:00 - 09:30 covers STATE_BRIDGE_TO_WORK (01:00 - 04:14) & morning wake stretch
                    time.isBefore(LocalTime.of(9, 30)) -> ProtocolStateId.STATE_BRIDGE_TO_WORK
                    // Monday 09:30 - 20:59 covers STATE_WORK_CORE (12:30 - 17:14) & pre-shift prep
                    time.isBefore(config.shiftStart) -> ProtocolStateId.STATE_WORK_CORE
                    // Monday 21:00+ is start of Monday night shift -> upcoming Tuesday 01:15 nap
                    else -> ProtocolStateId.STATE_WORK_NAP
                }
            }
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> {
                when {
                    // 00:00 - 05:59: Mid-shift window (01:15 - 01:35 nap + 02:00 caffeine + 03:00 nadir)
                    time.isBefore(config.shiftEnd) -> ProtocolStateId.STATE_WORK_NAP
                    // 06:00 - 20:59: Post-shift commute (06:00-09:00 sunglasses) + Work Core (12:30-17:14)
                    time.isBefore(config.shiftStart) -> ProtocolStateId.STATE_WORK_CORE
                    // 21:00+: Night shift started -> next sleep is 01:15 Adenosine Flush Nap
                    else -> ProtocolStateId.STATE_WORK_NAP
                }
            }
        }
    }

    /**
     * Shift_Window: Monday–Friday 21:00 to Tuesday–Saturday 06:00.
     */
    fun isWithinShiftWindow(now: ZonedDateTime): Boolean {
        val day = now.dayOfWeek
        val time = now.toLocalTime()

        val isEveningShiftStart = day in listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        ) && !time.isBefore(config.shiftStart)

        val isMorningShiftTail = day in listOf(
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY
        ) && time.isBefore(config.shiftEnd)

        return isEveningShiftStart || isMorningShiftTail
    }

    /**
     * Module 3: Weekend Anti-Nap / Boredom Lockout Timer.
     * Active during Saturday daytime wake stretch (after Bridge #1) and Sunday daytime wake stretch
     * (after Weekend Core or after an accidental early wake like Oct 18 04:27), especially 18:00-23:00.
     */
    private fun evaluateWeekendLockout(
        now: ZonedDateTime,
        state: ProtocolStateId,
        activeDeviation: DeviationRecoveryPlan?
    ): WeekendLockoutStatus {
        val day = now.dayOfWeek
        val time = now.toLocalTime()

        val isSatPostBridgeWake = day == DayOfWeek.SATURDAY && !time.isBefore(config.satBridgeEnd)
        val isSunPostCoreWake = day == DayOfWeek.SUNDAY && !time.isBefore( LocalTime.of(4, 0))
        val isDeviationLockoutActive = activeDeviation != null &&
            activeDeviation.enforceZeroNapLockout &&
            now.isAfter(activeDeviation.input.actualWakeTimestamp) &&
            now.isBefore(activeDeviation.nextAnchorTimestamp)

        val isLockoutActive = isSatPostBridgeWake || isSunPostCoreWake || isDeviationLockoutActive

        val isInBoredomDangerZone = (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) &&
            !time.isBefore(config.boredomZoneStart) &&
            time.isBefore(config.boredomZoneEnd)

        val next0100Anchor = when {
            activeDeviation != null && now.isBefore(activeDeviation.nextAnchorTimestamp) ->
                activeDeviation.nextAnchorTimestamp
            day == DayOfWeek.SATURDAY ->
                now.toLocalDate().plusDays(1).atTime(config.wkndCoreStart).atZone(now.zone)
            day == DayOfWeek.SUNDAY && time.isBefore(config.wkndCoreStart) ->
                now.toLocalDate().atTime(config.wkndCoreStart).atZone(now.zone)
            day == DayOfWeek.SUNDAY ->
                now.toLocalDate().plusDays(1).atTime(config.monBridgeStart).atZone(now.zone)
            else ->
                now.toLocalDate().atTime(config.workCoreStart).atZone(now.zone).let {
                    if (it.isAfter(now)) it else it.plusDays(1)
                }
        }

        val countdown = Duration.between(now, next0100Anchor).let {
            if (it.isNegative) Duration.ZERO else it
        }

        val headline = when {
            isInBoredomDangerZone ->
                "DANGER ZONE (18:00–23:00): EVENING BOREDOM NAP LOCKOUT"
            isLockoutActive ->
                "ZERO-NAP LOCKOUT ACTIVE — COUNTDOWN TO 01:00 ANCHOR"
            else ->
                "Work-Week Biphasic Schedule Active"
        }

        val subtext = when {
            isInBoredomDangerZone ->
                "High homeostatic pressure from capped bridge sleep makes a 20:00 nap lethal to your 01:00 anchor (Oct 17 Incident Trap). DO NOT NAP. Hold until 01:00."
            isLockoutActive ->
                "Zero naps permitted all day. Protect residual sleep pressure so you can lock into the 01:00 overnight block."
            else ->
                "Standard ultradian cycle triggers enabled for current phase."
        }

        return WeekendLockoutStatus(
            isLockoutActive = isLockoutActive,
            isInEveningBoredomDangerZone = isInBoredomDangerZone,
            nextAnchorTimestamp = next0100Anchor,
            countdownUntilAnchor = countdown,
            headline = headline,
            subtext = subtext
        )
    }

    private fun determinePrimaryBlock(
        state: ProtocolStateId,
        lockoutStatus: WeekendLockoutStatus,
        activeDeviation: DeviationRecoveryPlan?
    ): SleepBlockType {
        if (activeDeviation != null && activeDeviation.capPrecedingOvernightToTwoCycles &&
            state == ProtocolStateId.STATE_BRIDGE_TO_WORK
        ) {
            return SleepBlockType.BRIDGE_2_CYCLES
        }
        return when (state) {
            ProtocolStateId.STATE_WORK_CORE -> SleepBlockType.CORE_3_CYCLES
            ProtocolStateId.STATE_WORK_NAP -> SleepBlockType.FLUSH_NAP_20M
            ProtocolStateId.STATE_BRIDGE_TO_WKND -> SleepBlockType.BRIDGE_2_CYCLES
            ProtocolStateId.STATE_WKND_CORE -> SleepBlockType.CORE_3_CYCLES
            ProtocolStateId.STATE_BRIDGE_TO_WORK -> SleepBlockType.BRIDGE_2_CYCLES
        }
    }

    /**
     * Module 1 & 2: Dynamic Cycle Alarm Calculator + State-Aware Mode Guardrails.
     */
    private fun buildActionOptions(
        now: ZonedDateTime,
        state: ProtocolStateId,
        lockoutStatus: WeekendLockoutStatus,
        activeDeviation: DeviationRecoveryPlan?
    ): List<SleepActionOption> {
        val nowMillis = now.toInstant().toEpochMilli()

        return SleepBlockType.entries.map { blockType ->
            val durationMin = config.durationMinutesFor(blockType)
            val triggerMillis = config.calculateTargetTriggerMillis(nowMillis, blockType)
            val wakeTime = now.plusMinutes(durationMin.toLong())

            val (permission, badge, reason) = evaluateGuardrailForBlock(
                now = now,
                state = state,
                blockType = blockType,
                wakeTime = wakeTime,
                lockoutStatus = lockoutStatus,
                activeDeviation = activeDeviation
            )

            SleepActionOption(
                blockType = blockType,
                durationMinutes = durationMin,
                calculatedWakeTime = wakeTime,
                calculatedTriggerMillis = triggerMillis,
                permission = permission,
                guardrailBadge = badge,
                guardrailReason = reason
            )
        }.sortedBy { it.permission.ordinal }
    }

    private fun evaluateGuardrailForBlock(
        now: ZonedDateTime,
        state: ProtocolStateId,
        blockType: SleepBlockType,
        wakeTime: ZonedDateTime,
        lockoutStatus: WeekendLockoutStatus,
        activeDeviation: DeviationRecoveryPlan?
    ): Triple<GuardrailPermission, String, String> {
        val wakeStr = DeviationRecoveryCalculator.formatTime(wakeTime)

        return when (blockType) {
            SleepBlockType.CORE_3_CYCLES -> {
                when (state) {
                    ProtocolStateId.STATE_WORK_CORE -> Triple(
                        GuardrailPermission.PRIMARY_RECOMMENDED,
                        "PRIMARY ANCHOR (3 CYCLES)",
                        "Optimal 4h 44m core block (3 × 90m + 14m latency). Wakes at $wakeStr before 21:00 shift."
                    )
                    ProtocolStateId.STATE_WKND_CORE -> {
                        if (lockoutStatus.isInEveningBoredomDangerZone) {
                            Triple(
                                GuardrailPermission.WARN_INSOMNIA_OR_CIRCADIAN_RISK,
                                "EARLY ONSET WARNING",
                                "Starting a 3-cycle block before 01:00 risks shifting your circadian wake window too early."
                            )
                        } else {
                            Triple(
                                GuardrailPermission.PRIMARY_RECOMMENDED,
                                "PRIMARY WKND CORE (3 CYCLES)",
                                "Full 4h 44m recovery block anchored at 01:00 -> wakes at $wakeStr."
                            )
                        }
                    }
                    ProtocolStateId.STATE_BRIDGE_TO_WORK -> Triple(
                        GuardrailPermission.WARN_INSOMNIA_OR_CIRCADIAN_RISK,
                        "GUARDRAIL WARNING: INSOMNIA RISK",
                        "DO NOT select 3 cycles on Sunday night / Monday 01:00! Waking at $wakeStr fully rested leaves <7h before 12:30 STATE_WORK_CORE, causing pre-shift insomnia."
                    )
                    ProtocolStateId.STATE_BRIDGE_TO_WKND -> Triple(
                        GuardrailPermission.WARN_INSOMNIA_OR_CIRCADIAN_RISK,
                        "GUARDRAIL WARNING: OVER-REST",
                        "Saturday post-shift sleep must be capped at 2 cycles (3h 14m) so residual sleep pressure remains for 01:00 Saturday night."
                    )
                    ProtocolStateId.STATE_WORK_NAP -> Triple(
                        GuardrailPermission.WARN_INSOMNIA_OR_CIRCADIAN_RISK,
                        "SHIFT WINDOW CONFLICT",
                        "Currently in mid-shift window (21:00–06:00). Only the 20m Adenosine Flush Nap is permitted."
                    )
                }
            }

            SleepBlockType.BRIDGE_2_CYCLES -> {
                when (state) {
                    ProtocolStateId.STATE_BRIDGE_TO_WORK -> Triple(
                        GuardrailPermission.PRIMARY_RECOMMENDED,
                        "LOCKED PRIMARY CTA (2-CYCLE BRIDGE)",
                        "Mandatory 3h 14m bridge (2 × 90m + 14m latency). Waking at $wakeStr guarantees ~8.25h wakefulness before Monday 12:30 Work Core."
                    )
                    ProtocolStateId.STATE_BRIDGE_TO_WKND -> Triple(
                        GuardrailPermission.PRIMARY_RECOMMENDED,
                        "PRIMARY POST-SHIFT BRIDGE (2 CYCLES)",
                        "Capped 3h 14m bridge wakes at $wakeStr, preserving sleep pressure for 01:00 Saturday night."
                    )
                    ProtocolStateId.STATE_WORK_CORE -> Triple(
                        GuardrailPermission.ALLOWED_SECONDARY,
                        "COMPRESSED FALLBACK",
                        "Use 2-Cycle Bridge ($wakeStr wake) only if starting late after 14:00 to protect 21:00 shift start."
                    )
                    else -> Triple(
                        GuardrailPermission.ALLOWED_SECONDARY,
                        "SECONDARY OPTION",
                        "2-Cycle Bridge (+3h 14m) waking at $wakeStr."
                    )
                }
            }

            SleepBlockType.FLUSH_NAP_20M -> {
                if (lockoutStatus.isLockoutActive) {
                    val dangerPrefix = if (lockoutStatus.isInEveningBoredomDangerZone) {
                        "BOREDOM DANGER ZONE (18:00–23:00): "
                    } else {
                        "WEEKEND ZERO-NAP LOCKOUT: "
                    }
                    Triple(
                        GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL,
                        "LOCKED — ZERO NAPS PERMITTED",
                        "${dangerPrefix}20m nap is disabled so an evening nap cannot hijack your 01:00 overnight anchor (Oct 17 Incident Prevention)."
                    )
                } else if (state == ProtocolStateId.STATE_WORK_NAP) {
                    Triple(
                        GuardrailPermission.PRIMARY_RECOMMENDED,
                        "PRIMARY MID-SHIFT FLUSH (20M)",
                        "Clears adenosine before 03:00 circadian nadir.Wakes at $wakeStr with mandatory Anti-Backslide Check."
                    )
                } else {
                    Triple(
                        GuardrailPermission.ALLOWED_SECONDARY,
                        "EMERGENCY 20M FLUSH",
                        "Strictly capped at 20m (wakes at $wakeStr) with mandatory Wake-Up Challenge & 5m Anti-Backslide verification."
                    )
                }
            }
        }
    }

    private fun buildAdvisories(
        now: ZonedDateTime,
        state: ProtocolStateId,
        isInsideShift: Boolean,
        lockoutStatus: WeekendLockoutStatus,
        activeDeviation: DeviationRecoveryPlan?
    ): List<PhysiologicalAdvisory> {
        val list = mutableListOf<PhysiologicalAdvisory>()
        val time = now.toLocalTime()
        val day = now.dayOfWeek

        // 1. Deviation override banner if active
        if (activeDeviation != null) {
            list.add(
                PhysiologicalAdvisory(
                    title = "Active Deviation Recovery: ${activeDeviation.headline}",
                    detail = activeDeviation.physiologicalAnalysis.firstOrNull() ?: "",
                    severity = AdvisorySeverity.HIGH_ALERT_LOCKOUT
                )
            )
        }

        // 2. Weekend Boredom Lockout (18:00 - 23:00)
        if (lockoutStatus.isInEveningBoredomDangerZone) {
            list.add(
                PhysiologicalAdvisory(
                    title = "18:00–23:00 Evening Boredom Danger Zone",
                    detail = "Do NOT initiate a 20m nap. High sleep pressure from your 2-cycle bridge will cause you to sleep through or backslide into a 5-cycle crash.",
                    severity = AdvisorySeverity.HIGH_ALERT_LOCKOUT
                )
            )
        } else if (lockoutStatus.isLockoutActive) {
            list.add(
                PhysiologicalAdvisory(
                    title = "Zero-Nap Weekend Stretch Active",
                    detail = "Hold wakefulness until 01:00 (${DeviationRecoveryCalculator.formatDuration(lockoutStatus.countdownUntilAnchor)} remaining).",
                    severity = AdvisorySeverity.HIGH_ALERT_LOCKOUT
                )
            )
        }

        // 3. Sunglasses Commute Rule (06:00 - 09:00 Tue - Sat)
        val isPostShiftMorning = day in listOf(
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY
        ) && !time.isBefore(config.sunglassesWindowStart) && time.isBefore(config.sunglassesWindowEnd)

        if (isPostShiftMorning) {
            list.add(
                PhysiologicalAdvisory(
                    title = "06:00–09:00 Commute Daylight Blunting",
                    detail = "Wear dark sunglasses on commute home to blunt suprachiasmatic nucleus daylight signaling before 12:30 core sleep.",
                    severity = AdvisorySeverity.PHYSIOLOGICAL_TRIGGER
                )
            )
        }

        // 4. Mid-Shift Caffeine & Adenosine Flush Rule
        if (isInsideShift) {
            if (time.isAfter(LocalTime.of(20, 59)) || time.isBefore(config.caffeinePermittedTime)) {
                list.add(
                    PhysiologicalAdvisory(
                        title = "Caffeine Gate: Delay Until 02:00",
                        detail = "Do not consume sugar-free caffeine before the 01:15–01:35 Adenosine Flush Nap. Clear adenosine first, then dose at 02:00.",
                        severity = AdvisorySeverity.PHYSIOLOGICAL_TRIGGER
                    )
                )
            } else if (!time.isBefore(config.caffeinePermittedTime) && time.isBefore(LocalTime.of(4, 30))) {
                list.add(
                    PhysiologicalAdvisory(
                        title = "02:00 Caffeine Window Open | 03:00 Nadir Guard",
                        detail = "Post-flush sugar-free caffeine permitted now to bridge the 03:00 circadian nadir with <10h time-awake since 17:14.",
                        severity = AdvisorySeverity.INFO
                    )
                )
            }
        }

        // 5. Monday 04:14 -> 12:30 Pressure Accumulation Stretch
        if (day == DayOfWeek.MONDAY && !time.isBefore(config.monBridgeEnd) && time.isBefore(config.workCoreStart)) {
            val untilWorkCore = Duration.between(
                now,
                now.toLocalDate().atTime(config.workCoreStart).atZone(now.zone)
            )
            list.add(
                PhysiologicalAdvisory(
                    title = "Monday Homeostatic Pressure Build (04:14–12:30)",
                    detail = "Remain awake (${DeviationRecoveryCalculator.formatDuration(untilWorkCore)} left until 12:30). Your capped 2-cycle bridge guarantees sufficient pressure for STATE_WORK_CORE.",
                    severity = AdvisorySeverity.PHYSIOLOGICAL_TRIGGER
                )
            )
        }

        return list
    }

    private fun buildPhaseHeadlines(
        now: ZonedDateTime,
        state: ProtocolStateId,
        isInsideShift: Boolean,
        lockoutStatus: WeekendLockoutStatus,
        activeDeviation: DeviationRecoveryPlan?
    ): Pair<String, String> {
        if (activeDeviation != null && lockoutStatus.isLockoutActive) {
            return "${state.codeName} (RECOVERY OVERRIDE)" to
                "Deviation Recovery Active: Zero naps permitted until ${DeviationRecoveryCalculator.formatDayTime(lockoutStatus.nextAnchorTimestamp)}."
        }
        return when (state) {
            ProtocolStateId.STATE_WORK_CORE ->
                "STATE_WORK_CORE • 3-Cycle Workday Anchor" to
                    "Target window 12:30 – 17:14 (4h 44m). Keeps time-awake <10h by 03:00 shift nadir."
            ProtocolStateId.STATE_WORK_NAP ->
                "STATE_WORK_NAP • Mid-Shift Adenosine Flush" to
                    if (isInsideShift) {
                        "Active Shift (21:00–06:00). Take 20m Flush Nap at 01:15; delay caffeine until 02:00."
                    } else {
                        "Upcoming Mid-Shift Flush Nap (01:15 – 01:35, 20m cap)."
                    }
            ProtocolStateId.STATE_BRIDGE_TO_WKND ->
                "STATE_BRIDGE_TO_WKND • 2-Cycle Bridge #1" to
                    "Post-shift Saturday morning (~08:00 – ~11:14, 3h 14m cap). Builds pressure for 01:00 Sat night."
            ProtocolStateId.STATE_WKND_CORE ->
                "STATE_WKND_CORE • 3-Cycle Weekend Block" to
                    if (lockoutStatus.isLockoutActive) {
                        "Zero-Nap Lockout until 01:00 (${DeviationRecoveryCalculator.formatDuration(lockoutStatus.countdownUntilAnchor)} left)."
                    } else {
                        "Sunday 01:00 – 05:44 (4h 44m). Preserves night-shift anchor while granting Sunday daytime wakefulness."
                    }
            ProtocolStateId.STATE_BRIDGE_TO_WORK ->
                "STATE_BRIDGE_TO_WORK • 2-Cycle Bridge #2" to
                    "Monday 01:00 – 04:14 (3h 14m cap). Guarantees ~8.25h wakefulness before Monday 12:30 Work Core."
        }
    }
}
