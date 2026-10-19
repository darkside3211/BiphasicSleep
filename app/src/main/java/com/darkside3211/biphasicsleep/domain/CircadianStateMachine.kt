package com.darkside3211.biphasicsleep.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Module 1, 2 & 3: Weekly State Machine Engine, State-Aware Hard-Lock Guardrails,
 * Active Wake-Window Hard Lockout (Section 4.B), and Dynamic 20m Nap Lockout Calculator (Section 4.C).
 *
 * Document Version: 2.0 (Hard-Lock Guardrail & Dynamic Nap Cutoff Patch)
 */
class CircadianStateMachine(
    private val config: SleepProtocolConfig = SleepProtocolConfig()
) {

    fun evaluate(
        now: ZonedDateTime,
        activeDeviation: DeviationRecoveryPlan? = null,
        lastActualWakeTime: ZonedDateTime? = null,
        completedAnchorAt: ZonedDateTime? = null
    ): PhaseEvaluation {
        val nextAnchor = resolveNextCoreOrBridgeAnchor(now)
        val primaryWindowStatus = evaluatePrimaryAnchorWindow(
            now = now,
            nextAnchor = nextAnchor,
            completedAnchorAt = completedAnchorAt
        )
        val activeOrUpcomingState = primaryWindowStatus.activeStateId
        val isInsideShift = isWithinShiftWindow(now)
        val weekendLockoutStatus = evaluateWeekendLockout(now, nextAnchor.second, activeDeviation)
        val napEvaluation = evaluateNapLockout(
            now = now,
            nextAnchor = nextAnchor,
            lastActualWake = lastActualWakeTime,
            activeDeviation = activeDeviation,
            weekendLockout = weekendLockoutStatus,
            isInsideShift = isInsideShift
        )
        val actionOptions = buildActionOptions(
            now = now,
            primaryWindowStatus = primaryWindowStatus,
            napEvaluation = napEvaluation,
            activeDeviation = activeDeviation
        )
        val advisories = buildAdvisories(
            now = now,
            primaryWindowStatus = primaryWindowStatus,
            isInsideShift = isInsideShift,
            lockoutStatus = weekendLockoutStatus,
            activeDeviation = activeDeviation,
            napEvaluation = napEvaluation
        )
        val (headline, subheadline) = buildPhaseHeadlines(
            now = now,
            primaryWindowStatus = primaryWindowStatus,
            isInsideShift = isInsideShift,
            lockoutStatus = weekendLockoutStatus,
            activeDeviation = activeDeviation
        )

        val primaryBlock = primaryWindowStatus.permittedBlockType
            ?: if (isInsideShift && isInsideNapWindow(now.toLocalTime())) {
                SleepBlockType.FLUSH_NAP_20M
            } else {
                nextAnchor.second.let {
                    if (it == ProtocolStateId.STATE_BRIDGE_TO_WKND || it == ProtocolStateId.STATE_BRIDGE_TO_WORK) {
                        SleepBlockType.BRIDGE_2_CYCLES
                    } else {
                        SleepBlockType.CORE_3_CYCLES
                    }
                }
            }

        return PhaseEvaluation(
            evaluatedAt = now,
            activeOrUpcomingState = activeOrUpcomingState,
            phaseStatusHeadline = headline,
            phaseSubheadline = subheadline,
            isInsideShiftWindow = isInsideShift,
            primaryRecommendedBlock = primaryBlock,
            actionOptions = actionOptions,
            primaryAnchorWindow = primaryWindowStatus,
            napEvaluation = napEvaluation,
            weekendLockoutStatus = weekendLockoutStatus,
            advisories = advisories,
            activeDeviationOverride = activeDeviation
        )
    }

    /**
     * Determines whether the current time falls inside an active launch window (Section 4.A),
     * or transitions into STATE_WAKE_LOCKOUT (Section 4.B).
     */
    fun evaluatePrimaryAnchorWindow(
        now: ZonedDateTime,
        nextAnchor: Pair<ZonedDateTime, ProtocolStateId>,
        completedAnchorAt: ZonedDateTime?
    ): PrimaryAnchorWindowStatus {
        val day = now.dayOfWeek
        val time = now.toLocalTime()
        val nowMillis = now.toInstant().toEpochMilli()

        // Check if an anchor block was recently completed in this window (anti-rearm cooldown)
        val justCompletedAnchor = completedAnchorAt != null &&
            Duration.between(completedAnchorAt, now).toMinutes() in 0..180

        var permittedBlockType: SleepBlockType? = null
        var activeState: ProtocolStateId? = null

        if (!justCompletedAnchor) {
            when (day) {
                DayOfWeek.MONDAY -> {
                    when {
                        // Monday Early AM Bridge #2 (00:00 - 02:30 window) -> STATE_BRIDGE_TO_WORK
                        isInsideTimeWindow(time, config.monBridgeLaunchStart, config.monBridgeLaunchEnd) -> {
                            activeState = ProtocolStateId.STATE_BRIDGE_TO_WORK
                            permittedBlockType = SleepBlockType.BRIDGE_2_CYCLES
                        }
                        // Monday Afternoon Work Core (11:30 - 14:00 window) -> STATE_WORK_CORE
                        isInsideTimeWindow(time, config.workCoreLaunchStart, config.workCoreLaunchEnd) -> {
                            activeState = ProtocolStateId.STATE_WORK_CORE
                            permittedBlockType = SleepBlockType.CORE_3_CYCLES
                        }
                    }
                }
                DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> {
                    when {
                        // Work Core Launch Window (11:30 - 14:00) -> STATE_WORK_CORE
                        isInsideTimeWindow(time, config.workCoreLaunchStart, config.workCoreLaunchEnd) -> {
                            activeState = ProtocolStateId.STATE_WORK_CORE
                            permittedBlockType = SleepBlockType.CORE_3_CYCLES
                        }
                    }
                }
                DayOfWeek.SATURDAY -> {
                    when {
                        // Saturday Morning Bridge #1 (06:30 - 09:30 window) -> STATE_BRIDGE_TO_WKND
                        isInsideTimeWindow(time, config.satBridgeLaunchStart, config.satBridgeLaunchEnd) -> {
                            activeState = ProtocolStateId.STATE_BRIDGE_TO_WKND
                            permittedBlockType = SleepBlockType.BRIDGE_2_CYCLES
                        }
                    }
                }
                DayOfWeek.SUNDAY -> {
                    when {
                        // Sunday Early AM Weekend Core (00:00 - 02:30 window) -> STATE_WKND_CORE
                        isInsideTimeWindow(time, config.wkndCoreLaunchStart, config.wkndCoreLaunchEnd) -> {
                            activeState = ProtocolStateId.STATE_WKND_CORE
                            permittedBlockType = SleepBlockType.CORE_3_CYCLES
                        }
                    }
                }
            }
        }

        // Check if inside Tue–Sat mid-shift nap window (01:00 - 02:00)
        val isShiftNapWindow = isWithinShiftWindow(now) &&
            isInsideTimeWindow(time, config.workNapLaunchStart, config.workNapLaunchEnd)

        val isInActiveCoreOrBridgeWindow = activeState != null && permittedBlockType != null
        val governingState = when {
            isInActiveCoreOrBridgeWindow -> activeState!!
            isShiftNapWindow -> ProtocolStateId.STATE_WORK_NAP
            else -> ProtocolStateId.STATE_WAKE_LOCKOUT
        }

        val permittedAction = if (isInActiveCoreOrBridgeWindow) {
            val block = permittedBlockType!!
            val dur = config.durationMinutesFor(block)
            val wake = now.plusMinutes(dur.toLong())
            val wakeStr = DeviationRecoveryCalculator.formatTime(wake)
            val badge = when (block) {
                SleepBlockType.CORE_3_CYCLES -> "PRIMARY ANCHOR (3 CYCLES)"
                SleepBlockType.BRIDGE_2_CYCLES -> "PRIMARY BRIDGE (2 CYCLES)"
                SleepBlockType.FLUSH_NAP_20M -> "MID-SHIFT FLUSH (20M)"
            }
            val reason = when (governingState) {
                ProtocolStateId.STATE_WORK_CORE ->
                    "Optimal 4h 44m core block (3 × 90m + 14m latency). Wakes at $wakeStr before 21:00 shift."
                ProtocolStateId.STATE_BRIDGE_TO_WKND ->
                    "Capped 3h 14m bridge wakes at $wakeStr, preserving sleep pressure for 01:00 Saturday night."
                ProtocolStateId.STATE_WKND_CORE ->
                    "Full 4h 44m recovery block anchored at 01:00 -> wakes at $wakeStr."
                ProtocolStateId.STATE_BRIDGE_TO_WORK ->
                    "Mandatory 3h 14m bridge (2 × 90m + 14m latency). Waking at $wakeStr guarantees ~8.25h wakefulness before Monday 12:30 Work Core."
                else -> "Scheduled ultradian anchor trigger."
            }
            SleepActionOption(
                blockType = permittedBlockType,
                durationMinutes = dur,
                calculatedWakeTime = wake,
                calculatedTriggerMillis = config.calculateTargetTriggerMillis(nowMillis, permittedBlockType),
                permission = GuardrailPermission.PRIMARY_RECOMMENDED,
                guardrailBadge = badge,
                guardrailReason = reason
            )
        } else {
            null
        }

        val countdown = Duration.between(now, nextAnchor.first).let {
            if (it.isNegative) Duration.ZERO else it
        }

        val countdownStr = DeviationRecoveryCalculator.formatDuration(countdown)
        val nextTimeStr = DeviationRecoveryCalculator.formatTime(nextAnchor.first)
        val lockoutHeadline = "WAKE LOCKOUT ACTIVE — Building Homeostatic Pressure"
        val lockoutSubtext = "Next Anchor: ${nextAnchor.second.codeName} at $nextTimeStr in $countdownStr"

        return PrimaryAnchorWindowStatus(
            isInActiveLaunchWindow = isInActiveCoreOrBridgeWindow,
            activeStateId = governingState,
            permittedBlockType = permittedBlockType,
            permittedActionOption = permittedAction,
            nextScheduledAnchorState = nextAnchor.second,
            nextScheduledAnchorTime = nextAnchor.first,
            countdownToNextAnchor = countdown,
            wakeLockoutHeadline = lockoutHeadline,
            wakeLockoutSubtext = lockoutSubtext
        )
    }

    /**
     * Module 3 & Section 4.C: Dynamic 20-Minute Nap Lockout Calculator.
     * Evaluates Rule 1 (Pre-Anchor 4.5h Cutoff), Rule 2 (Post-Wake 90m Cooldown),
     * Rule 3 (Weekend Evening Danger Zone 18:00–01:00), and Rule 4 (Permitted Nap Windows).
     */
    /**
     * Module 3 & Section 4.C: Dynamic 20-Minute Nap Lockout Calculator convenience overload.
     */
    fun evaluateNapLockout(
        now: ZonedDateTime,
        lastActualWake: ZonedDateTime? = null,
        activeDeviation: DeviationRecoveryPlan? = null
    ): NapLockoutEvaluation {
        val nextAnchor = resolveNextCoreOrBridgeAnchor(now)
        val weekendLockout = evaluateWeekendLockout(now, nextAnchor.second, activeDeviation)
        val isInsideShift = isWithinShiftWindow(now)
        return evaluateNapLockout(
            now = now,
            nextAnchor = nextAnchor,
            lastActualWake = lastActualWake,
            activeDeviation = activeDeviation,
            weekendLockout = weekendLockout,
            isInsideShift = isInsideShift
        )
    }

    fun evaluateNapLockout(
        now: ZonedDateTime,
        nextAnchor: Pair<ZonedDateTime, ProtocolStateId>,
        lastActualWake: ZonedDateTime?,
        activeDeviation: DeviationRecoveryPlan?,
        weekendLockout: WeekendLockoutStatus,
        isInsideShift: Boolean
    ): NapLockoutEvaluation {
        val time = now.toLocalTime()
        val day = now.dayOfWeek
        val scheduledWake = resolveMostRecentScheduledWake(now)
        val effectiveLastWake = when {
            lastActualWake != null && lastActualWake.isAfter(scheduledWake) -> lastActualWake
            else -> scheduledWake
        }

        val timeSinceLastWake = if (now.isAfter(effectiveLastWake)) {
            Duration.between(effectiveLastWake, now)
        } else {
            Duration.ZERO
        }

        val timeUntilNextAnchor = if (nextAnchor.first.isAfter(now)) {
            Duration.between(now, nextAnchor.first)
        } else {
            Duration.ZERO
        }

        val wakeTime = now.plusMinutes(config.flushNapMinutes.toLong())
        val wakeStr = DeviationRecoveryCalculator.formatTime(wakeTime)
        val nextAnchorTimeStr = DeviationRecoveryCalculator.formatTime(nextAnchor.first)

        // 1. Rule 2: Post-Wake 90m Cooldown / Anti-Backslide (Highest Priority immediately after wake-up)
        val isInsidePostWakeCooldown = timeSinceLastWake.toMinutes() in 0L..<config.postWakeCooldownMinutes.toLong()
        if (isInsidePostWakeCooldown) {
            val cooldownRemaining = Duration.ofMinutes(config.postWakeCooldownMinutes.toLong()) - timeSinceLastWake
            return NapLockoutEvaluation(
                isHardLocked = true,
                ruleTriggered = NapLockoutRule.POST_WAKE_90M_COOLDOWN,
                statusHeadline = "POST-WAKE COOLDOWN (90M)",
                lockoutReason = "LOCKED: Post-Wake Anti-Backslide Cooldown (90m). Get out of bed to anchor wakefulness.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime
            )
        }

        // 2. Rule 3: Weekend Evening Danger Zone (18:00 - 01:00)
        val isWeekendEveningDangerZone = when (day) {
            DayOfWeek.SATURDAY -> time.hour >= 18
            DayOfWeek.SUNDAY -> time.hour < 1 || time.hour >= 18
            DayOfWeek.MONDAY -> time.hour < 1
            else -> false
        }
        if (isWeekendEveningDangerZone) {
            return NapLockoutEvaluation(
                isHardLocked = true,
                ruleTriggered = NapLockoutRule.WEEKEND_EVENING_DANGER_ZONE,
                statusHeadline = "DANGER ZONE (18:00–01:00): BOREDOM NAP LOCKOUT",
                lockoutReason = "LOCKED: Weekend Evening Danger Zone (18:00–01:00). Prevents boredom naps from converting into early 5-cycle crashes (Oct 17 Incident Prevention).",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime
            )
        }

        // 3. Deviation Zero-Nap Lockout Override
        if (activeDeviation != null && activeDeviation.enforceZeroNapLockout && now.isBefore(activeDeviation.nextAnchorTimestamp)) {
            val untilOverride = Duration.between(now, activeDeviation.nextAnchorTimestamp)
            return NapLockoutEvaluation(
                isHardLocked = true,
                ruleTriggered = NapLockoutRule.DEVIATION_ZERO_NAP_LOCKOUT,
                statusHeadline = "DEVIATION RECOVERY ZERO-NAP LOCKOUT",
                lockoutReason = "LOCKED: Active Deviation Recovery requires zero naps until ${DeviationRecoveryCalculator.formatDayTime(activeDeviation.nextAnchorTimestamp)} (${DeviationRecoveryCalculator.formatDuration(untilOverride)} left) to rebuild homeostatic sleep pressure.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime
            )
        }

        // 4. Weekend Zero-Nap Stretch (Saturday daytime after Bridge #1 or Sunday daytime after Core)
        if (weekendLockout.isLockoutActive) {
            return NapLockoutEvaluation(
                isHardLocked = true,
                ruleTriggered = NapLockoutRule.OUTSIDE_PERMITTED_NAP_WINDOW,
                statusHeadline = "WEEKEND ZERO-NAP PROTOCOL",
                lockoutReason = "LOCKED: Zero naps permitted all day weekend to protect residual sleep pressure for 01:00 overnight anchor.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime
            )
        }

        // 5. Rule 1: Pre-Anchor 4.5h Cutoff (< 270 minutes before next anchor)
        val isInsidePreAnchorCutoff = timeUntilNextAnchor.toMinutes() < config.preAnchorNapCutoffMinutes.toLong()
        if (isInsidePreAnchorCutoff) {
            return NapLockoutEvaluation(
                isHardLocked = true,
                ruleTriggered = NapLockoutRule.PRE_ANCHOR_4_5H_CUTOFF,
                statusHeadline = "PRE-ANCHOR 4.5H CUTOFF ACTIVE",
                lockoutReason = "LOCKED: < 4.5h until $nextAnchorTimeStr anchor. Napping now will bleed off required sleep pressure.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime
            )
        }

        // 6. Rule 4: Permitted Nap Windows
        val isMidShiftWindow = isInsideShift && isInsideTimeWindow(time, config.workNapLaunchStart, config.workNapLaunchEnd)
        if (isMidShiftWindow) {
            return NapLockoutEvaluation(
                isHardLocked = false,
                ruleTriggered = NapLockoutRule.NONE_PERMITTED,
                statusHeadline = "MID-SHIFT ADENOSINE FLUSH (20M)",
                lockoutReason = "UNLOCKED: Clears adenosine before 03:00 circadian nadir. Wakes at $wakeStr with mandatory 5m anti-backslide check.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime
            )
        }

        val isPermittedDaytimeWakeStretch = timeSinceLastWake.toMinutes() >= config.postWakeCooldownMinutes.toLong() &&
            timeUntilNextAnchor.toMinutes() >= config.preAnchorNapCutoffMinutes.toLong()

        if (isPermittedDaytimeWakeStretch) {
            return NapLockoutEvaluation(
                isHardLocked = false,
                ruleTriggered = NapLockoutRule.NONE_PERMITTED,
                statusHeadline = "DAYTIME ADENOSINE FLUSH (20M)",
                lockoutReason = "UNLOCKED: Daytime power flush permitted (>90m since wake, >4.5h before $nextAnchorTimeStr anchor). Wakes at $wakeStr.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime
            )
        }

        return NapLockoutEvaluation(
            isHardLocked = true,
            ruleTriggered = NapLockoutRule.OUTSIDE_PERMITTED_NAP_WINDOW,
            statusHeadline = "NAP LOCKED",
            lockoutReason = "LOCKED: Outside permitted 20m nap window. Protect homeostatic sleep pressure for scheduled anchors.",
            timeSinceLastWake = timeSinceLastWake,
            timeUntilNextAnchor = timeUntilNextAnchor,
            nextAnchorTargetTime = nextAnchor.first,
            calculatedWakeTime = wakeTime
        )
    }

    /**
     * Resolves the next Core or Bridge anchor timestamp and state.
     */
    fun resolveNextCoreOrBridgeAnchor(now: ZonedDateTime): Pair<ZonedDateTime, ProtocolStateId> {
        val day = now.dayOfWeek
        val time = now.toLocalTime()
        val zone = now.zone

        return when (day) {
            DayOfWeek.SATURDAY -> {
                if (time.isBefore(config.satBridgeStart)) {
                    now.toLocalDate().atTime(config.satBridgeStart).atZone(zone) to ProtocolStateId.STATE_BRIDGE_TO_WKND
                } else {
                    now.toLocalDate().plusDays(1).atTime(config.wkndCoreStart).atZone(zone) to ProtocolStateId.STATE_WKND_CORE
                }
            }
            DayOfWeek.SUNDAY -> {
                if (time.isBefore(config.wkndCoreStart)) {
                    now.toLocalDate().atTime(config.wkndCoreStart).atZone(zone) to ProtocolStateId.STATE_WKND_CORE
                } else {
                    now.toLocalDate().plusDays(1).atTime(config.monBridgeStart).atZone(zone) to ProtocolStateId.STATE_BRIDGE_TO_WORK
                }
            }
            DayOfWeek.MONDAY -> {
                if (time.isBefore(config.monBridgeStart)) {
                    now.toLocalDate().atTime(config.monBridgeStart).atZone(zone) to ProtocolStateId.STATE_BRIDGE_TO_WORK
                } else if (time.isBefore(config.workCoreStart)) {
                    now.toLocalDate().atTime(config.workCoreStart).atZone(zone) to ProtocolStateId.STATE_WORK_CORE
                } else {
                    now.toLocalDate().plusDays(1).atTime(config.workCoreStart).atZone(zone) to ProtocolStateId.STATE_WORK_CORE
                }
            }
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> {
                if (time.isBefore(config.workCoreStart)) {
                    now.toLocalDate().atTime(config.workCoreStart).atZone(zone) to ProtocolStateId.STATE_WORK_CORE
                } else {
                    now.toLocalDate().plusDays(1).atTime(config.workCoreStart).atZone(zone) to ProtocolStateId.STATE_WORK_CORE
                }
            }
            DayOfWeek.FRIDAY -> {
                if (time.isBefore(config.workCoreStart)) {
                    now.toLocalDate().atTime(config.workCoreStart).atZone(zone) to ProtocolStateId.STATE_WORK_CORE
                } else {
                    now.toLocalDate().plusDays(1).atTime(config.satBridgeStart).atZone(zone) to ProtocolStateId.STATE_BRIDGE_TO_WKND
                }
            }
        }
    }

    /**
     * Resolves the most recent scheduled wake-up timestamp to anchor Rule 2 (90m Post-Wake Cooldown).
     */
    fun resolveMostRecentScheduledWake(now: ZonedDateTime): ZonedDateTime {
        val day = now.dayOfWeek
        val time = now.toLocalTime()
        val zone = now.zone

        return when (day) {
            DayOfWeek.MONDAY -> {
                when {
                    !time.isBefore(config.workCoreEnd) -> now.toLocalDate().atTime(config.workCoreEnd).atZone(zone)
                    !time.isBefore(config.monBridgeEnd) -> now.toLocalDate().atTime(config.monBridgeEnd).atZone(zone)
                    else -> now.toLocalDate().minusDays(1).atTime(config.wkndCoreEnd).atZone(zone)
                }
            }
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> {
                when {
                    !time.isBefore(config.workCoreEnd) -> now.toLocalDate().atTime(config.workCoreEnd).atZone(zone)
                    else -> now.toLocalDate().minusDays(1).atTime(config.workCoreEnd).atZone(zone)
                }
            }
            DayOfWeek.SATURDAY -> {
                when {
                    !time.isBefore(config.satBridgeEnd) -> now.toLocalDate().atTime(config.satBridgeEnd).atZone(zone)
                    else -> now.toLocalDate().minusDays(1).atTime(config.workCoreEnd).atZone(zone)
                }
            }
            DayOfWeek.SUNDAY -> {
                when {
                    !time.isBefore(config.wkndCoreEnd) -> now.toLocalDate().atTime(config.wkndCoreEnd).atZone(zone)
                    else -> now.toLocalDate().minusDays(1).atTime(config.satBridgeEnd).atZone(zone)
                }
            }
        }
    }

    /**
     * Maps any day/time of the week to the governing ProtocolStateId.
     * In v2.0, outside scheduled launch windows, returns STATE_WAKE_LOCKOUT.
     */
    fun determineState(now: ZonedDateTime): ProtocolStateId {
        val nextAnchor = resolveNextCoreOrBridgeAnchor(now)
        val status = evaluatePrimaryAnchorWindow(now, nextAnchor, null)
        return status.activeStateId
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

    private fun isInsideNapWindow(time: LocalTime): Boolean {
        return isInsideTimeWindow(time, config.workNapLaunchStart, config.workNapLaunchEnd)
    }

    private fun isInsideTimeWindow(time: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (end.isAfter(start)) {
            !time.isBefore(start) && time.isBefore(end)
        } else {
            !time.isBefore(start) || time.isBefore(end)
        }
    }

    /**
     * Module 3: Weekend Anti-Nap / Boredom Lockout Timer.
     */
    private fun evaluateWeekendLockout(
        now: ZonedDateTime,
        nextAnchorState: ProtocolStateId,
        activeDeviation: DeviationRecoveryPlan?
    ): WeekendLockoutStatus {
        val day = now.dayOfWeek
        val time = now.toLocalTime()

        val isSatPostBridgeWake = day == DayOfWeek.SATURDAY && !time.isBefore(config.satBridgeEnd)
        val isSunPostCoreWake = day == DayOfWeek.SUNDAY && !time.isBefore(config.wkndCoreEnd)
        val isDeviationLockoutActive = activeDeviation != null &&
            activeDeviation.enforceZeroNapLockout &&
            now.isAfter(activeDeviation.input.actualWakeTimestamp) &&
            now.isBefore(activeDeviation.nextAnchorTimestamp)

        val isLockoutActive = isSatPostBridgeWake || isSunPostCoreWake || isDeviationLockoutActive

        val isInBoredomDangerZone = when (day) {
            DayOfWeek.SATURDAY -> time.hour >= 18
            DayOfWeek.SUNDAY -> time.hour < 1 || time.hour >= 18
            DayOfWeek.MONDAY -> time.hour < 1
            else -> false
        }

        val next0100Anchor = when {
            activeDeviation != null && now.isBefore(activeDeviation.nextAnchorTimestamp) ->
                activeDeviation.nextAnchorTimestamp
            day == DayOfWeek.SATURDAY ->
                now.toLocalDate().plusDays(1).atTime(config.wkndCoreStart).atZone(now.zone)
            day == DayOfWeek.SUNDAY && time.isBefore(config.wkndCoreStart) ->
                now.toLocalDate().atTime(config.wkndCoreStart).atZone(now.zone)
            day == DayOfWeek.SUNDAY ->
                now.toLocalDate().plusDays(1).atTime(config.monBridgeStart).atZone(now.zone)
            day == DayOfWeek.MONDAY && time.isBefore(config.monBridgeStart) ->
                now.toLocalDate().atTime(config.monBridgeStart).atZone(now.zone)
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
                "DANGER ZONE (18:00–01:00): EVENING BOREDOM NAP LOCKOUT"
            isLockoutActive ->
                "ZERO-NAP LOCKOUT ACTIVE — COUNTDOWN TO 01:00 ANCHOR"
            else ->
                "Work-Week Biphasic Schedule Active"
        }

        val subtext = when {
            isInBoredomDangerZone ->
                "High homeostatic pressure makes a boredom nap fatal to your 01:00 anchor (Oct 17 Incident Trap). Hold until 01:00."
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

    /**
     * Builds action options list.
     * During launch windows, the single permitted cycle block is marked PRIMARY_RECOMMENDED.
     * During STATE_WAKE_LOCKOUT, full cycle blocks are set to LOCKED_ZERO_NAP_PROTOCOL.
     */
    private fun buildActionOptions(
        now: ZonedDateTime,
        primaryWindowStatus: PrimaryAnchorWindowStatus,
        napEvaluation: NapLockoutEvaluation,
        activeDeviation: DeviationRecoveryPlan?
    ): List<SleepActionOption> {
        val nowMillis = now.toInstant().toEpochMilli()
        val list = mutableListOf<SleepActionOption>()

        SleepBlockType.entries.forEach { blockType ->
            val durationMin = config.durationMinutesFor(blockType)
            val triggerMillis = config.calculateTargetTriggerMillis(nowMillis, blockType)
            val wakeTime = now.plusMinutes(durationMin.toLong())
            val wakeStr = DeviationRecoveryCalculator.formatTime(wakeTime)

            val (permission, badge, reason) = when (blockType) {
                SleepBlockType.CORE_3_CYCLES -> {
                    when {
                        primaryWindowStatus.isInActiveLaunchWindow && primaryWindowStatus.permittedBlockType == SleepBlockType.CORE_3_CYCLES -> {
                            Triple(
                                GuardrailPermission.PRIMARY_RECOMMENDED,
                                "PRIMARY ANCHOR (3 CYCLES)",
                                "Optimal 4h 44m core block (3 × 90m + 14m latency). Wakes at $wakeStr."
                            )
                        }
                        primaryWindowStatus.isInActiveLaunchWindow && primaryWindowStatus.permittedBlockType == SleepBlockType.BRIDGE_2_CYCLES -> {
                            Triple(
                                GuardrailPermission.WARN_INSOMNIA_OR_CIRCADIAN_RISK,
                                "HARD-LOCKED: 2-CYCLE ONLY",
                                "Only 2-Cycle Bridge is permitted in this phase. 3-Cycle Core is locked out to prevent oversleep and pre-shift insomnia."
                            )
                        }
                        else -> {
                            Triple(
                                GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL,
                                "WAKE LOCKOUT ACTIVE",
                                "Outside scheduled launch window. Building homeostatic sleep pressure until ${DeviationRecoveryCalculator.formatTime(primaryWindowStatus.nextScheduledAnchorTime)}."
                            )
                        }
                    }
                }
                SleepBlockType.BRIDGE_2_CYCLES -> {
                    when {
                        primaryWindowStatus.isInActiveLaunchWindow && primaryWindowStatus.permittedBlockType == SleepBlockType.BRIDGE_2_CYCLES -> {
                            Triple(
                                GuardrailPermission.PRIMARY_RECOMMENDED,
                                "PRIMARY BRIDGE (2 CYCLES)",
                                "Mandatory 3h 14m bridge (2 × 90m + 14m latency). Wakes at $wakeStr."
                            )
                        }
                        primaryWindowStatus.isInActiveLaunchWindow && primaryWindowStatus.permittedBlockType == SleepBlockType.CORE_3_CYCLES -> {
                            Triple(
                                GuardrailPermission.ALLOWED_SECONDARY,
                                "COMPRESSED FALLBACK",
                                "Compressed 2-Cycle Bridge fallback waking at $wakeStr."
                            )
                        }
                        else -> {
                            Triple(
                                GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL,
                                "WAKE LOCKOUT ACTIVE",
                                "Outside scheduled launch window. Building homeostatic sleep pressure until ${DeviationRecoveryCalculator.formatTime(primaryWindowStatus.nextScheduledAnchorTime)}."
                            )
                        }
                    }
                }
                SleepBlockType.FLUSH_NAP_20M -> {
                    if (napEvaluation.isHardLocked) {
                        Triple(
                            GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL,
                            "LOCKED (20M NAP)",
                            napEvaluation.lockoutReason
                        )
                    } else if (primaryWindowStatus.activeStateId == ProtocolStateId.STATE_WORK_NAP) {
                        Triple(
                            GuardrailPermission.PRIMARY_RECOMMENDED,
                            "PRIMARY MID-SHIFT FLUSH (20M)",
                            napEvaluation.lockoutReason
                        )
                    } else {
                        Triple(
                            GuardrailPermission.ALLOWED_SECONDARY,
                            "PERMITTED 20M FLUSH",
                            napEvaluation.lockoutReason
                        )
                    }
                }
            }

            list.add(
                SleepActionOption(
                    blockType = blockType,
                    durationMinutes = durationMin,
                    calculatedWakeTime = wakeTime,
                    calculatedTriggerMillis = triggerMillis,
                    permission = permission,
                    guardrailBadge = badge,
                    guardrailReason = reason
                )
            )
        }

        return list.sortedBy { it.permission.ordinal }
    }

    private fun buildAdvisories(
        now: ZonedDateTime,
        primaryWindowStatus: PrimaryAnchorWindowStatus,
        isInsideShift: Boolean,
        lockoutStatus: WeekendLockoutStatus,
        activeDeviation: DeviationRecoveryPlan?,
        napEvaluation: NapLockoutEvaluation
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

        // 2. Nap Lockout Advisory if locked
        if (napEvaluation.isHardLocked) {
            list.add(
                PhysiologicalAdvisory(
                    title = napEvaluation.statusHeadline,
                    detail = napEvaluation.lockoutReason,
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

        // 5. Monday Pressure Accumulation Stretch (04:14 -> 12:30)
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
        primaryWindowStatus: PrimaryAnchorWindowStatus,
        isInsideShift: Boolean,
        lockoutStatus: WeekendLockoutStatus,
        activeDeviation: DeviationRecoveryPlan?
    ): Pair<String, String> {
        if (activeDeviation != null && lockoutStatus.isLockoutActive) {
            return "DEVIATION RECOVERY ACTIVE" to
                "Strict Zero-Nap Lockout until ${DeviationRecoveryCalculator.formatDayTime(lockoutStatus.nextAnchorTimestamp)}."
        }

        if (!primaryWindowStatus.isInActiveLaunchWindow) {
            val countdownStr = DeviationRecoveryCalculator.formatDuration(primaryWindowStatus.countdownToNextAnchor)
            val nextState = primaryWindowStatus.nextScheduledAnchorState
            val nextTimeStr = DeviationRecoveryCalculator.formatTime(primaryWindowStatus.nextScheduledAnchorTime)
            return "WAKE LOCKOUT ACTIVE" to
                "Building Homeostatic Pressure • Next Anchor: ${nextState.title} at $nextTimeStr in $countdownStr."
        }

        return when (primaryWindowStatus.activeStateId) {
            ProtocolStateId.STATE_WORK_CORE ->
                "STATE_WORK_CORE • 3-Cycle Workday Anchor" to
                    "Launch window open (11:30–14:00). Target 4h 44m block wakes before 21:00 shift."
            ProtocolStateId.STATE_WORK_NAP ->
                "STATE_WORK_NAP • Mid-Shift Adenosine Flush" to
                    "Launch window open (01:00–02:00). Take 20m Flush Nap; delay caffeine until 02:00."
            ProtocolStateId.STATE_BRIDGE_TO_WKND ->
                "STATE_BRIDGE_TO_WKND • 2-Cycle Bridge #1" to
                    "Saturday morning bridge (3h 14m cap). Builds pressure for 01:00 Sat night."
            ProtocolStateId.STATE_WKND_CORE ->
                "STATE_WKND_CORE • 3-Cycle Weekend Block" to
                    "Weekend core recovery block anchored at 01:00 (4h 44m)."
            ProtocolStateId.STATE_BRIDGE_TO_WORK ->
                "STATE_BRIDGE_TO_WORK • 2-Cycle Bridge #2" to
                    "Mandatory 2-cycle bridge (3h 14m cap). Guarantees ~8.25h wakefulness before Monday 12:30 Work Core."
            ProtocolStateId.STATE_WAKE_LOCKOUT ->
                "WAKE LOCKOUT ACTIVE" to
                    "Building Homeostatic Pressure."
        }
    }
}
