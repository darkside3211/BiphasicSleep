package com.darkside3211.biphasicsleep.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Module 1, 2 & 3: Weekly State Machine Engine, Event-Driven Cooldowns,
 * Dynamic Cycle Truncation (UNFULFILLED_ANCHOR_DELAYED), and Sliding 20m Nap Calculator (v2.2).
 */
class CircadianStateMachine(
    private val config: SleepProtocolConfig = SleepProtocolConfig()
) {

    fun evaluate(
        now: ZonedDateTime,
        activeDeviation: DeviationRecoveryPlan? = null,
        lastActualWakeTime: ZonedDateTime? = null,
        completedAnchorAt: ZonedDateTime? = null,
        completedNapAt: ZonedDateTime? = null
    ): PhaseEvaluation {
        val nominalNextAnchor = resolveNextCoreOrBridgeAnchor(now)
        val effectiveCompletedAnchor = completedAnchorAt ?: lastActualWakeTime
        val primaryWindowStatus = evaluatePrimaryAnchorWindow(
            now = now,
            nextAnchor = nominalNextAnchor,
            completedAnchorAt = effectiveCompletedAnchor
        )
        val effectiveNextAnchor = primaryWindowStatus.nextScheduledAnchorTime to primaryWindowStatus.nextScheduledAnchorState
        val activeOrUpcomingState = primaryWindowStatus.activeStateId
        val isInsideShift = isWithinShiftWindow(now)
        val weekendLockoutStatus = evaluateWeekendLockout(
            now = now,
            nextAnchorState = effectiveNextAnchor.second,
            activeDeviation = activeDeviation,
            isAnchorCompletedForCurrentCycle = primaryWindowStatus.isAnchorCompletedForCurrentCycle,
            effectiveNextAnchorTime = effectiveNextAnchor.first
        )
        val napEvaluation = evaluateNapLockout(
            now = now,
            nextAnchor = effectiveNextAnchor,
            lastActualWake = lastActualWakeTime ?: completedAnchorAt ?: completedNapAt,
            activeDeviation = activeDeviation,
            weekendLockout = weekendLockoutStatus,
            isInsideShift = isInsideShift,
            completedNapAt = completedNapAt,
            completedAnchorAt = effectiveCompletedAnchor
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
            ?: if (isInsideShift && !napEvaluation.isHardLocked) {
                SleepBlockType.FLUSH_NAP_20M
            } else {
                effectiveNextAnchor.second.let {
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
     * Determines whether the current time falls inside:
     * 1. An active scheduled launch window (when not yet completed),
     * 2. UNFULFILLED_ANCHOR_DELAYED (when the launch window passed with 0m logged sleep and recovery cycles fit), or
     * 3. STATE_WAKE_LOCKOUT (after sleep is completed or when inside mandatory wake stretch).
     */
    fun evaluatePrimaryAnchorWindow(
        now: ZonedDateTime,
        nextAnchor: Pair<ZonedDateTime, ProtocolStateId>,
        completedAnchorAt: ZonedDateTime?
    ): PrimaryAnchorWindowStatus {
        val day = now.dayOfWeek
        val time = now.toLocalTime()
        val nowMillis = now.toInstant().toEpochMilli()

        val isAnchorCompleted = isAnchorCompletedForCurrentCycle(now, completedAnchorAt)

        var scheduledBlockType: SleepBlockType? = null
        var scheduledState: ProtocolStateId? = null

        if (!isAnchorCompleted) {
            when (day) {
                DayOfWeek.MONDAY -> {
                    when {
                        // Monday Early AM Bridge #2 (00:00 - 02:30 window) -> STATE_BRIDGE_TO_WORK
                        isInsideTimeWindow(time, config.monBridgeLaunchStart, config.monBridgeLaunchEnd) -> {
                            scheduledState = ProtocolStateId.STATE_BRIDGE_TO_WORK
                            scheduledBlockType = SleepBlockType.BRIDGE_2_CYCLES
                        }
                        // Monday Afternoon Work Core (11:30 - 14:00 window) -> STATE_WORK_CORE
                        isInsideTimeWindow(time, config.workCoreLaunchStart, config.workCoreLaunchEnd) -> {
                            scheduledState = ProtocolStateId.STATE_WORK_CORE
                            scheduledBlockType = SleepBlockType.CORE_3_CYCLES
                        }
                    }
                }
                DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> {
                    when {
                        // Work Core Launch Window (11:30 - 14:00) -> STATE_WORK_CORE
                        isInsideTimeWindow(time, config.workCoreLaunchStart, config.workCoreLaunchEnd) -> {
                            scheduledState = ProtocolStateId.STATE_WORK_CORE
                            scheduledBlockType = SleepBlockType.CORE_3_CYCLES
                        }
                    }
                }
                DayOfWeek.SATURDAY -> {
                    when {
                        // Saturday Morning Bridge #1 (06:30 - 09:30 window) -> STATE_BRIDGE_TO_WKND
                        isInsideTimeWindow(time, config.satBridgeLaunchStart, config.satBridgeLaunchEnd) -> {
                            scheduledState = ProtocolStateId.STATE_BRIDGE_TO_WKND
                            scheduledBlockType = SleepBlockType.BRIDGE_2_CYCLES
                        }
                    }
                }
                DayOfWeek.SUNDAY -> {
                    when {
                        // Sunday Early AM Weekend Core (00:00 - 02:30 window) -> STATE_WKND_CORE
                        isInsideTimeWindow(time, config.wkndCoreLaunchStart, config.wkndCoreLaunchEnd) -> {
                            scheduledState = ProtocolStateId.STATE_WKND_CORE
                            scheduledBlockType = SleepBlockType.CORE_3_CYCLES
                        }
                    }
                }
            }
        }

        val isInScheduledLaunchWindow = scheduledState != null && scheduledBlockType != null

        // If outside scheduled launch window and sleep was NOT completed for current cycle,
        // evaluate Backward-Calculated Sleep Ceiling for UNFULFILLED_ANCHOR_DELAYED
        val delayedEvaluation = if (!isInScheduledLaunchWindow && !isAnchorCompleted) {
            evaluateDelayedAnchorCeiling(now, nextAnchor)
        } else {
            null
        }

        val isInActiveOrDelayedSleepWindow = isInScheduledLaunchWindow || delayedEvaluation != null
        val effectivePermittedBlock = scheduledBlockType ?: delayedEvaluation?.recommendedBlock
        val effectiveNextAnchorTime = delayedEvaluation?.adjustedNextAnchorTime ?: nextAnchor.first
        val isAnchorPushedBack = delayedEvaluation?.isAnchorPushedBack ?: false

        // Check if inside Tue–Sat mid-shift nap window (01:00 - 02:00)
        val isShiftNapWindow = isWithinShiftWindow(now) &&
            isInsideTimeWindow(time, config.workNapLaunchStart, config.workNapLaunchEnd)

        val governingState = when {
            isInScheduledLaunchWindow -> scheduledState!!
            delayedEvaluation != null -> ProtocolStateId.UNFULFILLED_ANCHOR_DELAYED
            isShiftNapWindow -> ProtocolStateId.STATE_WORK_NAP
            else -> ProtocolStateId.STATE_WAKE_LOCKOUT
        }

        val leewayNote = buildWeekdayCommuteLeewayNote(now, effectivePermittedBlock)

        val permittedAction = if (isInActiveOrDelayedSleepWindow && effectivePermittedBlock != null) {
            val dur = config.durationMinutesFor(effectivePermittedBlock)
            val wake = now.plusMinutes(dur.toLong())
            val wakeStr = DeviationRecoveryCalculator.formatTime(wake)
            val badge = if (delayedEvaluation != null) {
                delayedEvaluation.badgeLabel
            } else {
                when (effectivePermittedBlock) {
                    SleepBlockType.CORE_3_CYCLES -> "PRIMARY ANCHOR (3 CYCLES)"
                    SleepBlockType.BRIDGE_2_CYCLES -> "PRIMARY BRIDGE (2 CYCLES)"
                    SleepBlockType.RECOVERY_1_CYCLE -> "RESCUE BLOCK (1 CYCLE)"
                    SleepBlockType.FLUSH_NAP_20M -> "MID-SHIFT FLUSH (20M)"
                }
            }
            val reason = if (delayedEvaluation != null) {
                delayedEvaluation.reasonText
            } else {
                when (governingState) {
                    ProtocolStateId.STATE_WORK_CORE ->
                        "Full 4h 44m sleep (3 × 90m cycles + 14m to fall asleep). Wakes at $wakeStr before your 21:00 shift."
                    ProtocolStateId.STATE_BRIDGE_TO_WKND ->
                        "Shorter 3h 14m morning sleep wakes at $wakeStr, leaving you tired enough to sleep at 01:00 tonight."
                    ProtocolStateId.STATE_WKND_CORE ->
                        "Full 4h 44m weekend night sleep anchored at 01:00 -> wakes at $wakeStr."
                    ProtocolStateId.STATE_BRIDGE_TO_WORK ->
                        "Short 3h 14m sleep (2 cycles + 14m). Waking at $wakeStr gives ~8.25h awake so you can sleep at 12:30 PM Monday."
                    else -> "Scheduled sleep window open."
                }
            }
            SleepActionOption(
                blockType = effectivePermittedBlock,
                durationMinutes = dur,
                calculatedWakeTime = wake,
                calculatedTriggerMillis = config.calculateTargetTriggerMillis(nowMillis, effectivePermittedBlock),
                permission = GuardrailPermission.PRIMARY_RECOMMENDED,
                guardrailBadge = badge,
                guardrailReason = reason
            )
        } else {
            null
        }

        val flexibleCycleOptions = if (!isAnchorCompleted && !isWithinShiftWindow(now)) {
            buildFlexibleCycleOptions(now, effectivePermittedBlock, effectiveNextAnchorTime)
        } else {
            emptyList()
        }

        val countdown = Duration.between(now, effectiveNextAnchorTime).let {
            if (it.isNegative) Duration.ZERO else it
        }

        val countdownStr = DeviationRecoveryCalculator.formatDuration(countdown)
        val nextTimeStr = DeviationRecoveryCalculator.formatTime(effectiveNextAnchorTime)
        val lockoutHeadline = "WAKE LOCKOUT ACTIVE — Stay Awake Until $nextTimeStr"
        val lockoutSubtext = "Next Anchor: ${nextAnchor.second.codeName} (${nextAnchor.second.title}) at $nextTimeStr in $countdownStr"

        return PrimaryAnchorWindowStatus(
            isInActiveLaunchWindow = isInActiveOrDelayedSleepWindow,
            activeStateId = governingState,
            permittedBlockType = effectivePermittedBlock,
            permittedActionOption = permittedAction,
            nextScheduledAnchorState = nextAnchor.second,
            nextScheduledAnchorTime = effectiveNextAnchorTime,
            countdownToNextAnchor = countdown,
            wakeLockoutHeadline = lockoutHeadline,
            wakeLockoutSubtext = lockoutSubtext,
            isUnfulfilledAnchorDelayed = delayedEvaluation != null,
            isAnchorCompletedForCurrentCycle = isAnchorCompleted,
            nominalNextAnchorTime = nextAnchor.first,
            isAnchorPushedBack = isAnchorPushedBack,
            delayedRecoveryHeadline = delayedEvaluation?.badgeLabel,
            leewayAdvisoryText = leewayNote,
            availableCycleOptions = flexibleCycleOptions
        )
    }

    private data class DelayedAnchorEvaluation(
        val recommendedBlock: SleepBlockType,
        val adjustedNextAnchorTime: ZonedDateTime,
        val isAnchorPushedBack: Boolean,
        val badgeLabel: String,
        val reasonText: String
    )

    /**
     * Backward-Calculated Sleep Ceiling for UNFULFILLED_ANCHOR_DELAYED:
     * Steps down 3 Cycles (4h 44m) -> 2 Cycles (3h 14m) -> 1 Cycle (1h 44m)
     * while preserving 6h–7h homeostatic wake buffer before NextAnchorStart
     * (allowing up to +1h anchor pushback from 01:00 to 02:00 when wake time crosses the standard threshold).
     */
    private fun evaluateDelayedAnchorCeiling(
        now: ZonedDateTime,
        nextAnchor: Pair<ZonedDateTime, ProtocolStateId>
    ): DelayedAnchorEvaluation? {
        if (isWithinShiftWindow(now)) return null

        val day = now.dayOfWeek
        val time = now.toLocalTime()
        val zone = now.zone

        // Determine if an anchor window for the current day/period has already opened/passed
        // or if the user wants early/late daytime sleep on a workday/weekend
        val isWeekendOrSundayDelayed = (day == DayOfWeek.SATURDAY && !time.isBefore(config.satBridgeLaunchEnd)) ||
            (day == DayOfWeek.SUNDAY && !time.isBefore(config.wkndCoreLaunchEnd))

        val isWorkdayAfternoonDelayed = day in listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        ) && !time.isBefore(config.workCoreLaunchEnd) && time.isBefore(config.shiftStart)

        if (!isWeekendOrSundayDelayed && !isWorkdayAfternoonDelayed) {
            return null
        }

        // Case 1: Workday Late Afternoon Arrival (Mon–Fri after 14:00 before 21:00 shift)
        if (isWorkdayAfternoonDelayed) {
            val shiftStartDt = now.toLocalDate().atTime(config.shiftStart).atZone(zone)
            val candidates = listOf(
                SleepBlockType.CORE_3_CYCLES to 90L,   // Prefer >= 90m prep leeway before 21:00 shift
                SleepBlockType.BRIDGE_2_CYCLES to 60L, // Prefer >= 60m prep leeway before 21:00 shift
                SleepBlockType.RECOVERY_1_CYCLE to 30L // Rescue 1-cycle if >= 30m before 21:00 shift
            )
            for ((block, minLeewayMin) in candidates) {
                val durMin = config.durationMinutesFor(block)
                val wakeTime = now.plusMinutes(durMin.toLong())
                val leewayToShift = Duration.between(wakeTime, shiftStartDt).toMinutes()
                if (leewayToShift >= minLeewayMin) {
                    val wakeStr = DeviationRecoveryCalculator.formatTime(wakeTime)
                    val durStr = DeviationRecoveryCalculator.formatDuration(Duration.ofMinutes(durMin.toLong()))
                    val cyclesLabel = if (block.cyclesCount == 1) "1 CYCLE" else "${block.cyclesCount} CYCLES"
                    val badge = "DELAYED RECOVERY: $cyclesLabel ($durStr)"
                    val leewayStr = DeviationRecoveryCalculator.formatDuration(Duration.ofMinutes(leewayToShift))
                    val reason = "Late arrival adjustment: $durStr sleep wakes at $wakeStr, leaving $leewayStr to get ready before your 21:00 shift."
                    return DelayedAnchorEvaluation(
                        recommendedBlock = block,
                        adjustedNextAnchorTime = nextAnchor.first,
                        isAnchorPushedBack = false,
                        badgeLabel = badge,
                        reasonText = reason
                    )
                }
            }
            return null
        }

        // Case 2: Weekend / Overnight Anchor Delayed (e.g., Saturday 09:30+ -> Sunday 01:00 STATE_WKND_CORE)
        val nominalAnchorTime = nextAnchor.first
        val maxPushedAnchorTime = nominalAnchorTime.plusMinutes(config.maxAnchorPushbackMinutes.toLong())

        val candidates = listOf(
            SleepBlockType.CORE_3_CYCLES,
            SleepBlockType.BRIDGE_2_CYCLES,
            SleepBlockType.RECOVERY_1_CYCLE
        )

        for (block in candidates) {
            val durMin = config.durationMinutesFor(block)
            val wakeTime = now.plusMinutes(durMin.toLong())
            val bufferToNominalMin = Duration.between(wakeTime, nominalAnchorTime).toMinutes()
            val bufferToPushedMin = Duration.between(wakeTime, maxPushedAnchorTime).toMinutes()

            // For 3 cycles on Saturday daytime, require >= 8h (480m) before nominal 01:00 so afternoon arrivals step down to 2 cycles
            val requiredNominalForNoPushback = if (block == SleepBlockType.CORE_3_CYCLES && day == DayOfWeek.SATURDAY) {
                480L // 8h 00m before 01:00 (waking by 17:00)
            } else {
                config.standardWakeBufferMinutes.toLong() // 7h 00m (420m)
            }

            val fitsWithoutPushback = bufferToNominalMin >= requiredNominalForNoPushback
            val fitsWithPushback = block != SleepBlockType.CORE_3_CYCLES &&
                bufferToNominalMin >= config.minimumWakeBufferMinutes.toLong() &&
                bufferToPushedMin >= config.standardWakeBufferMinutes.toLong()

            if (fitsWithoutPushback || fitsWithPushback) {
                val pushbackActive = !fitsWithoutPushback && fitsWithPushback
                val effectiveAnchor = if (pushbackActive) maxPushedAnchorTime else nominalAnchorTime
                val effectiveBuffer = Duration.between(wakeTime, effectiveAnchor)

                val wakeStr = DeviationRecoveryCalculator.formatTime(wakeTime)
                val durStr = DeviationRecoveryCalculator.formatDuration(Duration.ofMinutes(durMin.toLong()))
                val nominalStr = DeviationRecoveryCalculator.formatTime(nominalAnchorTime)
                val effectiveAnchorStr = DeviationRecoveryCalculator.formatTime(effectiveAnchor)
                val bufferStr = DeviationRecoveryCalculator.formatDuration(effectiveBuffer)
                val cyclesLabel = if (block.cyclesCount == 1) "1 CYCLE" else "${block.cyclesCount} CYCLES"
                val badge = "DELAYED RECOVERY: $cyclesLabel ($durStr)"

                val reason = if (pushbackActive) {
                    "Zero sleep logged yet. $durStr sleep wakes at $wakeStr; shifts tonight's bedtime from $nominalStr -> $effectiveAnchorStr to preserve $bufferStr of awake time."
                } else {
                    "Zero sleep logged yet. $durStr recovery sleep wakes at $wakeStr, leaving $bufferStr of awake time before tonight's $effectiveAnchorStr sleep."
                }

                return DelayedAnchorEvaluation(
                    recommendedBlock = block,
                    adjustedNextAnchorTime = effectiveAnchor,
                    isAnchorPushedBack = pushbackActive,
                    badgeLabel = badge,
                    reasonText = reason
                )
            }
        }

        return null
    }

    /**
     * Checks whether the current cycle's core or bridge anchor was explicitly completed.
     */
    fun isAnchorCompletedForCurrentCycle(
        now: ZonedDateTime,
        completedAnchorAt: ZonedDateTime?
    ): Boolean {
        if (completedAnchorAt == null || completedAnchorAt.isAfter(now)) return false
        val minutesSinceCompletion = Duration.between(completedAnchorAt, now).toMinutes()
        if (minutesSinceCompletion > 18 * 60) return false

        val cycleWindowStart = resolveCurrentCycleWindowStart(now)
        return !completedAnchorAt.isBefore(cycleWindowStart)
    }

    private fun resolveCurrentCycleWindowStart(now: ZonedDateTime): ZonedDateTime {
        val day = now.dayOfWeek
        val time = now.toLocalTime()
        val zone = now.zone

        return when (day) {
            DayOfWeek.SATURDAY -> {
                if (!time.isBefore(config.satBridgeLaunchStart)) {
                    now.toLocalDate().atTime(config.satBridgeLaunchStart).atZone(zone)
                } else {
                    now.toLocalDate().minusDays(1).atTime(config.workCoreLaunchStart).atZone(zone)
                }
            }
            DayOfWeek.SUNDAY -> {
                now.toLocalDate().atTime(config.wkndCoreLaunchStart).atZone(zone)
            }
            DayOfWeek.MONDAY -> {
                if (!time.isBefore(config.workCoreLaunchStart)) {
                    now.toLocalDate().atTime(config.workCoreLaunchStart).atZone(zone)
                } else {
                    now.toLocalDate().atTime(config.monBridgeLaunchStart).atZone(zone)
                }
            }
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> {
                if (!time.isBefore(config.workCoreLaunchStart)) {
                    now.toLocalDate().atTime(config.workCoreLaunchStart).atZone(zone)
                } else {
                    now.toLocalDate().minusDays(1).atTime(config.workCoreLaunchStart).atZone(zone)
                }
            }
        }
    }

    private fun buildFlexibleCycleOptions(
        now: ZonedDateTime,
        recommendedBlock: SleepBlockType?,
        nextAnchorTime: ZonedDateTime
    ): List<SleepActionOption> {
        val nowMillis = now.toInstant().toEpochMilli()
        val fullCycleBlocks = listOf(
            SleepBlockType.CORE_3_CYCLES,
            SleepBlockType.BRIDGE_2_CYCLES,
            SleepBlockType.RECOVERY_1_CYCLE
        )
        return fullCycleBlocks.map { block ->
            val dur = config.durationMinutesFor(block)
            val wake = now.plusMinutes(dur.toLong())
            val wakeStr = DeviationRecoveryCalculator.formatTime(wake)
            val durStr = DeviationRecoveryCalculator.formatDuration(Duration.ofMinutes(dur.toLong()))
            val isRecommended = block == recommendedBlock
            SleepActionOption(
                blockType = block,
                durationMinutes = dur,
                calculatedWakeTime = wake,
                calculatedTriggerMillis = config.calculateTargetTriggerMillis(nowMillis, block),
                permission = if (isRecommended) GuardrailPermission.PRIMARY_RECOMMENDED else GuardrailPermission.ALLOWED_SECONDARY,
                guardrailBadge = if (isRecommended) "RECOMMENDED ($durStr)" else "${block.cyclesCount} CYCLE OPTION ($durStr)",
                guardrailReason = "Wakes at $wakeStr (${DeviationRecoveryCalculator.formatDuration(Duration.between(wake, nextAnchorTime).let { if (it.isNegative) Duration.ZERO else it })} before ${DeviationRecoveryCalculator.formatTime(nextAnchorTime)})"
            )
        }
    }

    /**
     * Non-rigid informational note on weekdays showing how much leeway a wake-up leaves
     * before the 18:00 (6:00 PM) travel/departure reference and 21:00 shift start.
     */
    private fun buildWeekdayCommuteLeewayNote(
        now: ZonedDateTime,
        blockType: SleepBlockType?
    ): String? {
        if (blockType == null || blockType == SleepBlockType.FLUSH_NAP_20M) return null
        val day = now.dayOfWeek
        if (day !in listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            return null
        }
        val time = now.toLocalTime()
        if (time.isBefore(LocalTime.of(8, 0)) || !time.isBefore(config.shiftStart)) return null

        val durMin = config.durationMinutesFor(blockType)
        val wake = now.plusMinutes(durMin.toLong())
        val wakeStr = DeviationRecoveryCalculator.formatTime(wake)
        val commuteTarget = now.toLocalDate().atTime(config.weekdayCommuteReferenceTime).atZone(now.zone)
        val shiftTarget = now.toLocalDate().atTime(config.shiftStart).atZone(now.zone)

        val before1800Min = Duration.between(wake, commuteTarget).toMinutes()
        val before2100Min = Duration.between(wake, shiftTarget).toMinutes().coerceAtLeast(0)
        val shiftLeewayStr = DeviationRecoveryCalculator.formatDuration(Duration.ofMinutes(before2100Min))

        return if (before1800Min > 0) {
            val commuteLeewayStr = DeviationRecoveryCalculator.formatDuration(Duration.ofMinutes(before1800Min))
            "Weekday Logistics Note: Waking at $wakeStr gives you $commuteLeewayStr before 18:00 (6:00 PM) departure ($shiftLeewayStr before 21:00 shift)."
        } else {
            "Weekday Logistics Note: Wakes at $wakeStr (after 18:00; leaves $shiftLeewayStr before 21:00 shift). Select 2 Cycles (3h 14m) if you need to leave before 18:00."
        }
    }

    /**
     * Module 3 & Section 4.C: Sliding 20-Minute Nap Lockout Calculator (v2.2).
     * Decouples post-wake cooldown from wall-clock assumptions and keeps the 20m nap
     * unlocked throughout the shift/wake period unless:
     * 1. Already completed in current period,
     * 2. Inside event-verified 90m post-wake cooldown,
     * 3. Weekend Evening Danger Zone (18:00–01:00) / confirmed weekend lockout / deviation override, or
     * 4. CurrentTime + 20m breaches the 4-hour proximity cutoff before NextCoreAnchorStart.
     */
    fun evaluateNapLockout(
        now: ZonedDateTime,
        lastActualWake: ZonedDateTime? = null,
        activeDeviation: DeviationRecoveryPlan? = null,
        completedNapAt: ZonedDateTime? = null,
        completedAnchorAt: ZonedDateTime? = null
    ): NapLockoutEvaluation {
        val nominalAnchor = resolveNextCoreOrBridgeAnchor(now)
        val effectiveCompletedAnchor = completedAnchorAt ?: lastActualWake
        val primaryStatus = evaluatePrimaryAnchorWindow(now, nominalAnchor, effectiveCompletedAnchor)
        val effectiveAnchor = primaryStatus.nextScheduledAnchorTime to primaryStatus.nextScheduledAnchorState
        val isInsideShift = isWithinShiftWindow(now)
        val weekendLockout = evaluateWeekendLockout(
            now = now,
            nextAnchorState = effectiveAnchor.second,
            activeDeviation = activeDeviation,
            isAnchorCompletedForCurrentCycle = primaryStatus.isAnchorCompletedForCurrentCycle,
            effectiveNextAnchorTime = effectiveAnchor.first
        )
        return evaluateNapLockout(
            now = now,
            nextAnchor = effectiveAnchor,
            lastActualWake = lastActualWake ?: completedAnchorAt ?: completedNapAt,
            activeDeviation = activeDeviation,
            weekendLockout = weekendLockout,
            isInsideShift = isInsideShift,
            completedNapAt = completedNapAt,
            completedAnchorAt = effectiveCompletedAnchor
        )
    }

    fun evaluateNapLockout(
        now: ZonedDateTime,
        nextAnchor: Pair<ZonedDateTime, ProtocolStateId>,
        lastActualWake: ZonedDateTime?,
        activeDeviation: DeviationRecoveryPlan?,
        weekendLockout: WeekendLockoutStatus,
        isInsideShift: Boolean,
        completedNapAt: ZonedDateTime? = null,
        completedAnchorAt: ZonedDateTime? = null
    ): NapLockoutEvaluation {
        val time = now.toLocalTime()
        val day = now.dayOfWeek

        // Strictly event-driven wake timestamp: NEVER assume sleep occurred from the wall clock
        val confirmedLastWake = listOfNotNull(lastActualWake, completedAnchorAt, completedNapAt)
            .filter { !it.isAfter(now) }
            .maxOrNull()

        val timeSinceLastWake: Duration? = if (confirmedLastWake != null) {
            Duration.between(confirmedLastWake, now)
        } else {
            null
        }

        val timeUntilNextAnchor = if (nextAnchor.first.isAfter(now)) {
            Duration.between(now, nextAnchor.first)
        } else {
            Duration.ZERO
        }

        val wakeTime = now.plusMinutes(config.flushNapMinutes.toLong())
        val wakeStr = DeviationRecoveryCalculator.formatTime(wakeTime)
        val nextAnchorTimeStr = DeviationRecoveryCalculator.formatTime(nextAnchor.first)

        // Check if a 20m nap was already completed in the current wake/shift period
        val napCompletedInCurrentPeriod = completedNapAt != null &&
            !completedNapAt.isAfter(now) &&
            Duration.between(completedNapAt, now).toHours() < 12 &&
            (completedAnchorAt == null || !completedNapAt.isBefore(completedAnchorAt))

        // 1. Rule: Post-Wake 90m Cooldown / Anti-Backslide (Event-Verified Only!)
        val isInsidePostWakeCooldown = timeSinceLastWake != null &&
            timeSinceLastWake.toMinutes() in 0L..<config.postWakeCooldownMinutes.toLong()
        if (isInsidePostWakeCooldown) {
            return NapLockoutEvaluation(
                isHardLocked = true,
                ruleTriggered = NapLockoutRule.POST_WAKE_90M_COOLDOWN,
                statusHeadline = "POST-WAKE COOLDOWN (90M)",
                lockoutReason = "LOCKED: Post-Wake Anti-Backslide Cooldown (90m). Get out of bed to anchor wakefulness.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime,
                isNapCompletedInCurrentPeriod = napCompletedInCurrentPeriod
            )
        }

        // 2. Rule: Nap Already Completed in Current Shift / Wake Period
        if (napCompletedInCurrentPeriod) {
            return NapLockoutEvaluation(
                isHardLocked = true,
                ruleTriggered = NapLockoutRule.NAP_ALREADY_COMPLETED_IN_PERIOD,
                statusHeadline = "LOCKED (Hard-Lock Rule) • NAP ALREADY TAKEN",
                lockoutReason = "LOCKED (Hard-Lock Rule): You already completed your 20m power nap for this period at ${DeviationRecoveryCalculator.formatTime(completedNapAt!!)}.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime,
                isNapCompletedInCurrentPeriod = true
            )
        }

        // 3. Rule: Weekend Evening Danger Zone (18:00 - 01:00)
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
                calculatedWakeTime = wakeTime,
                isNapCompletedInCurrentPeriod = false
            )
        }

        // 4. Rule: Deviation Zero-Nap Lockout Override
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
                calculatedWakeTime = wakeTime,
                isNapCompletedInCurrentPeriod = false
            )
        }

        // 5. Rule: Weekend Zero-Nap Stretch (Only active AFTER weekend morning sleep is actually completed!)
        if (weekendLockout.isLockoutActive) {
            return NapLockoutEvaluation(
                isHardLocked = true,
                ruleTriggered = NapLockoutRule.OUTSIDE_PERMITTED_NAP_WINDOW,
                statusHeadline = "WEEKEND ZERO-NAP PROTOCOL",
                lockoutReason = "LOCKED: Zero naps permitted after completing weekend sleep to protect sleep pressure for tonight's $nextAnchorTimeStr bedtime.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime,
                isNapCompletedInCurrentPeriod = false
            )
        }

        // 6. Rule: 4-Hour Proximity Cutoff (CurrentTime + 20m < NextCoreAnchorStart - NAP_PROXIMITY_CUTOFF)
        val postNapTimeUntilAnchor = Duration.between(wakeTime, nextAnchor.first)
        val breachesProximityCutoff = postNapTimeUntilAnchor.toMinutes() < config.napProximityCutoffMinutes.toLong()
        if (breachesProximityCutoff) {
            return NapLockoutEvaluation(
                isHardLocked = true,
                ruleTriggered = NapLockoutRule.PRE_ANCHOR_4_5H_CUTOFF,
                statusHeadline = "PRE-ANCHOR CUTOFF ACTIVE (< 4H POST-NAP)",
                lockoutReason = "LOCKED: < 4.5h until $nextAnchorTimeStr anchor (< 4h after 20m nap). Napping now will bleed off required sleep pressure.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime,
                isNapCompletedInCurrentPeriod = false
            )
        }

        // 7. Sliding Eligibility Window: Unlocked throughout shift and eligible wake period!
        if (isInsideShift) {
            return NapLockoutEvaluation(
                isHardLocked = false,
                ruleTriggered = NapLockoutRule.NONE_PERMITTED,
                statusHeadline = "MID-SHIFT 20-MIN POWER NAP • AVAILABLE",
                lockoutReason = "Available throughout your shift break. Wakes at $wakeStr (+20m) with a 5m check-in so you don't oversleep.",
                timeSinceLastWake = timeSinceLastWake,
                timeUntilNextAnchor = timeUntilNextAnchor,
                nextAnchorTargetTime = nextAnchor.first,
                calculatedWakeTime = wakeTime,
                isNapCompletedInCurrentPeriod = false
            )
        }

        return NapLockoutEvaluation(
            isHardLocked = false,
            ruleTriggered = NapLockoutRule.NONE_PERMITTED,
            statusHeadline = "DAYTIME 20-MIN POWER NAP • AVAILABLE",
            lockoutReason = "Available right now (more than 4h before your $nextAnchorTimeStr sleep). Wakes at $wakeStr (+20m).",
            timeSinceLastWake = timeSinceLastWake,
            timeUntilNextAnchor = timeUntilNextAnchor,
            nextAnchorTargetTime = nextAnchor.first,
            calculatedWakeTime = wakeTime,
            isNapCompletedInCurrentPeriod = false
        )
    }

    /**
     * Resolves the next nominal Core or Bridge anchor timestamp and state.
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
     * Resolves the most recent scheduled wake-up timestamp (retained for reference/tests,
     * no longer used to trigger phantom post-wake cooldowns).
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
     */
    fun determineState(now: ZonedDateTime, completedAnchorAt: ZonedDateTime? = null): ProtocolStateId {
        val nextAnchor = resolveNextCoreOrBridgeAnchor(now)
        val status = evaluatePrimaryAnchorWindow(now, nextAnchor, completedAnchorAt)
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

    private fun isInsideTimeWindow(time: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (end.isAfter(start)) {
            !time.isBefore(start) && time.isBefore(end)
        } else {
            !time.isBefore(start) || time.isBefore(end)
        }
    }

    /**
     * Module 3: Weekend Anti-Nap / Boredom Lockout Timer (Event-Driven).
     * Daytime Zero-Nap Lockout on Saturday/Sunday only activates if the morning sleep
     * block was actually completed (isAnchorCompletedForCurrentCycle == true), or during an
     * active Deviation Recovery override, or inside the 18:00–01:00 Evening Boredom Danger Zone.
     */
    private fun evaluateWeekendLockout(
        now: ZonedDateTime,
        nextAnchorState: ProtocolStateId,
        activeDeviation: DeviationRecoveryPlan?,
        isAnchorCompletedForCurrentCycle: Boolean,
        effectiveNextAnchorTime: ZonedDateTime
    ): WeekendLockoutStatus {
        val day = now.dayOfWeek
        val time = now.toLocalTime()

        val isSatPostBridgeConfirmed = day == DayOfWeek.SATURDAY &&
            !time.isBefore(config.satBridgeLaunchStart) &&
            isAnchorCompletedForCurrentCycle
        val isSunPostCoreConfirmed = day == DayOfWeek.SUNDAY &&
            !time.isBefore(config.wkndCoreLaunchStart) &&
            isAnchorCompletedForCurrentCycle
        val isDeviationLockoutActive = activeDeviation != null &&
            activeDeviation.enforceZeroNapLockout &&
            now.isAfter(activeDeviation.input.actualWakeTimestamp) &&
            now.isBefore(activeDeviation.nextAnchorTimestamp)

        val isInBoredomDangerZone = when (day) {
            DayOfWeek.SATURDAY -> time.hour >= 18
            DayOfWeek.SUNDAY -> time.hour < 1 || time.hour >= 18
            DayOfWeek.MONDAY -> time.hour < 1
            else -> false
        }

        val isLockoutActive = isSatPostBridgeConfirmed ||
            isSunPostCoreConfirmed ||
            isDeviationLockoutActive ||
            isInBoredomDangerZone

        val nextTargetAnchor = when {
            activeDeviation != null && now.isBefore(activeDeviation.nextAnchorTimestamp) ->
                activeDeviation.nextAnchorTimestamp
            else -> effectiveNextAnchorTime
        }

        val countdown = Duration.between(now, nextTargetAnchor).let {
            if (it.isNegative) Duration.ZERO else it
        }

        val targetTimeStr = DeviationRecoveryCalculator.formatTime(nextTargetAnchor)

        val headline = when {
            isInBoredomDangerZone ->
                "Evening Danger Zone (18:00–01:00): Hold Off Until $targetTimeStr"
            isLockoutActive ->
                "Stay Awake Window — Countdown to $targetTimeStr Bedtime"
            else ->
                "Schedule Active"
        }

        val subtext = when {
            isInBoredomDangerZone ->
                "Sleeping between 6:00 PM and 1:00 AM on the weekend easily turns into a 10-hour crash. Hold out until $targetTimeStr."
            isLockoutActive ->
                "You finished your earlier sleep. Avoid daytime naps so you can fall asleep easily at $targetTimeStr tonight."
            else ->
                "Dynamic cycle adjustments enabled for your current schedule."
        }

        return WeekendLockoutStatus(
            isLockoutActive = isLockoutActive,
            isInEveningBoredomDangerZone = isInBoredomDangerZone,
            nextAnchorTimestamp = nextTargetAnchor,
            countdownUntilAnchor = countdown,
            headline = headline,
            subtext = subtext
        )
    }

    /**
     * Builds action options list.
     * Preserves 3 canonical options (CORE_3_CYCLES, BRIDGE_2_CYCLES, FLUSH_NAP_20M) for backward test compatibility,
     * while PrimaryAnchorWindowStatus.availableCycleOptions provides the full 3c / 2c / 1c selector.
     */
    private fun buildActionOptions(
        now: ZonedDateTime,
        primaryWindowStatus: PrimaryAnchorWindowStatus,
        napEvaluation: NapLockoutEvaluation,
        activeDeviation: DeviationRecoveryPlan?
    ): List<SleepActionOption> {
        val nowMillis = now.toInstant().toEpochMilli()
        val list = mutableListOf<SleepActionOption>()

        val canonicalTypes = listOf(
            SleepBlockType.CORE_3_CYCLES,
            SleepBlockType.BRIDGE_2_CYCLES,
            SleepBlockType.FLUSH_NAP_20M
        )

        canonicalTypes.forEach { blockType ->
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
                                primaryWindowStatus.delayedRecoveryHeadline ?: "RECOMMENDED (3 CYCLES • 4h 44m)",
                                "Full 4h 44m sleep (3 × 90m + 14m latency). Wakes at $wakeStr."
                            )
                        }
                        primaryWindowStatus.isInActiveLaunchWindow -> {
                            Triple(
                                GuardrailPermission.ALLOWED_SECONDARY,
                                "3-CYCLE OPTION (4h 44m)",
                                "Full 4h 44m sleep waking at $wakeStr. Shorter 2-cycle sleep is recommended for this window."
                            )
                        }
                        else -> {
                            Triple(
                                GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL,
                                "STAY AWAKE WINDOW",
                                "Next scheduled sleep is at ${DeviationRecoveryCalculator.formatTime(primaryWindowStatus.nextScheduledAnchorTime)}."
                            )
                        }
                    }
                }
                SleepBlockType.BRIDGE_2_CYCLES -> {
                    when {
                        primaryWindowStatus.isInActiveLaunchWindow && primaryWindowStatus.permittedBlockType == SleepBlockType.BRIDGE_2_CYCLES -> {
                            Triple(
                                GuardrailPermission.PRIMARY_RECOMMENDED,
                                primaryWindowStatus.delayedRecoveryHeadline ?: "RECOMMENDED (2 CYCLES • 3h 14m)",
                                "Short 3h 14m sleep (2 × 90m + 14m latency). Wakes at $wakeStr."
                            )
                        }
                        primaryWindowStatus.isInActiveLaunchWindow -> {
                            Triple(
                                GuardrailPermission.ALLOWED_SECONDARY,
                                "2-CYCLE OPTION (3h 14m)",
                                "Shorter 3h 14m sleep waking at $wakeStr."
                            )
                        }
                        else -> {
                            Triple(
                                GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL,
                                "STAY AWAKE WINDOW",
                                "Next scheduled sleep is at ${DeviationRecoveryCalculator.formatTime(primaryWindowStatus.nextScheduledAnchorTime)}."
                            )
                        }
                    }
                }
                SleepBlockType.FLUSH_NAP_20M -> {
                    if (napEvaluation.isHardLocked) {
                        Triple(
                            GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL,
                            "LOCKED (Hard-Lock Rule)",
                            napEvaluation.lockoutReason
                        )
                    } else if (primaryWindowStatus.activeStateId == ProtocolStateId.STATE_WORK_NAP || isWithinShiftWindow(now)) {
                        Triple(
                            GuardrailPermission.PRIMARY_RECOMMENDED,
                            "UNLOCKED (20M SHIFT NAP)",
                            napEvaluation.lockoutReason
                        )
                    } else {
                        Triple(
                            GuardrailPermission.ALLOWED_SECONDARY,
                            "UNLOCKED (20M NAP)",
                            napEvaluation.lockoutReason
                        )
                    }
                }
                SleepBlockType.RECOVERY_1_CYCLE -> {
                    Triple(
                        GuardrailPermission.ALLOWED_SECONDARY,
                        "1-CYCLE RESCUE (1h 44m)",
                        "Single 90m cycle + 14m latency waking at $wakeStr."
                    )
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
                    title = "Active Recovery Plan: ${activeDeviation.headline}",
                    detail = activeDeviation.physiologicalAnalysis.firstOrNull() ?: "",
                    severity = AdvisorySeverity.HIGH_ALERT_LOCKOUT
                )
            )
        }

        // 2. Delayed Anchor Pushback Advisory
        if (primaryWindowStatus.isUnfulfilledAnchorDelayed && primaryWindowStatus.isAnchorPushedBack) {
            val nomStr = DeviationRecoveryCalculator.formatTime(primaryWindowStatus.nominalNextAnchorTime)
            val adjStr = DeviationRecoveryCalculator.formatTime(primaryWindowStatus.nextScheduledAnchorTime)
            list.add(
                PhysiologicalAdvisory(
                    title = "Tonight's Bedtime Shifted: $nomStr -> $adjStr (+1h Buffer)",
                    detail = "Because you are sleeping later today, tonight's bedtime is automatically pushed back from $nomStr to $adjStr so you still have 6–7+ hours awake to build sleep drive.",
                    severity = AdvisorySeverity.PHYSIOLOGICAL_TRIGGER
                )
            )
        }

        // 3. Weekday 6:00 PM Commute Logistics Note
        if (primaryWindowStatus.leewayAdvisoryText != null) {
            list.add(
                PhysiologicalAdvisory(
                    title = "Weekday 6:00 PM Travel / Prep Reference",
                    detail = primaryWindowStatus.leewayAdvisoryText,
                    severity = AdvisorySeverity.INFO
                )
            )
        }

        // 4. Nap Lockout Advisory if locked
        if (napEvaluation.isHardLocked) {
            list.add(
                PhysiologicalAdvisory(
                    title = napEvaluation.statusHeadline,
                    detail = napEvaluation.lockoutReason,
                    severity = AdvisorySeverity.HIGH_ALERT_LOCKOUT
                )
            )
        }

        // 5. Sunglasses Commute Rule (06:00 - 09:00 Tue - Sat)
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
                    title = "Morning Commute (06:00–09:00): Wear Dark Sunglasses",
                    detail = "Blocking bright morning sunlight on your drive home keeps your brain ready to fall asleep quickly during the day.",
                    severity = AdvisorySeverity.PHYSIOLOGICAL_TRIGGER
                )
            )
        }

        // 6. Mid-Shift Caffeine & Nap Rule
        if (isInsideShift) {
            if (time.isAfter(LocalTime.of(20, 59)) || time.isBefore(config.caffeinePermittedTime)) {
                list.add(
                    PhysiologicalAdvisory(
                        title = "Wait Until After Your Nap (~02:00) for Caffeine",
                        detail = "Hold off on energy drinks or coffee until after your 20-minute shift nap so you can fall asleep fast on break.",
                        severity = AdvisorySeverity.PHYSIOLOGICAL_TRIGGER
                    )
                )
            } else if (!time.isBefore(config.caffeinePermittedTime) && time.isBefore(LocalTime.of(4, 30))) {
                list.add(
                    PhysiologicalAdvisory(
                        title = "Caffeine Window Open (02:00–04:30)",
                        detail = "Sugar-free caffeine is fine now to power through the 03:00 AM energy dip while still wearing off before daytime sleep.",
                        severity = AdvisorySeverity.INFO
                    )
                )
            }
        }

        // 7. Monday Morning Awake Stretch (04:14 -> 12:30)
        if (day == DayOfWeek.MONDAY && !time.isBefore(config.monBridgeEnd) && time.isBefore(config.workCoreStart)) {
            val untilWorkCore = Duration.between(
                now,
                now.toLocalDate().atTime(config.workCoreStart).atZone(now.zone)
            )
            list.add(
                PhysiologicalAdvisory(
                    title = "Monday Morning Awake Window (Until 12:30 PM)",
                    detail = "Stay awake (${DeviationRecoveryCalculator.formatDuration(untilWorkCore)} left until 12:30 PM) so you can get a full 4h 44m sleep before Monday night's shift.",
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
            return "RECOVERY PLAN ACTIVE" to
                "Stay awake until ${DeviationRecoveryCalculator.formatDayTime(lockoutStatus.nextAnchorTimestamp)}."
        }

        if (primaryWindowStatus.isUnfulfilledAnchorDelayed) {
            val badge = primaryWindowStatus.delayedRecoveryHeadline ?: "DELAYED RECOVERY"
            val nextTimeStr = DeviationRecoveryCalculator.formatTime(primaryWindowStatus.nextScheduledAnchorTime)
            return badge to
                "Late arrival adjustment • Next bedtime target: $nextTimeStr."
        }

        if (!primaryWindowStatus.isInActiveLaunchWindow) {
            val countdownStr = DeviationRecoveryCalculator.formatDuration(primaryWindowStatus.countdownToNextAnchor)
            val nextState = primaryWindowStatus.nextScheduledAnchorState
            val nextTimeStr = DeviationRecoveryCalculator.formatTime(primaryWindowStatus.nextScheduledAnchorTime)
            return "STAY AWAKE WINDOW" to
                "Next Sleep: ${nextState.title} at $nextTimeStr in $countdownStr."
        }

        return when (primaryWindowStatus.activeStateId) {
            ProtocolStateId.STATE_WORK_CORE ->
                "Afternoon Workday Sleep (4h 44m)" to
                    "Sleep window open (11:30–14:00). Full 3-cycle sleep before your 21:00 shift."
            ProtocolStateId.STATE_WORK_NAP ->
                "20-Min Shift Power Nap" to
                    "Take your 20m break nap whenever your break starts; save caffeine for after."
            ProtocolStateId.STATE_BRIDGE_TO_WKND ->
                "Saturday Morning Sleep (3h 14m)" to
                    "Shorter 2-cycle morning sleep so you can sleep tonight at 01:00 AM."
            ProtocolStateId.STATE_WKND_CORE ->
                "Saturday Night Full Sleep (4h 44m)" to
                    "Weekend night sleep anchored at 01:00 AM (4h 44m)."
            ProtocolStateId.STATE_BRIDGE_TO_WORK ->
                "Sunday Night Reset Sleep (3h 14m)" to
                    "Short 2-cycle sleep (3h 14m) so you're tired enough for Monday's 12:30 PM sleep."
            ProtocolStateId.UNFULFILLED_ANCHOR_DELAYED ->
                (primaryWindowStatus.delayedRecoveryHeadline ?: "Late Sleep Adjustment") to
                    "Dynamically adjusted sleep block."
            ProtocolStateId.STATE_WAKE_LOCKOUT ->
                "Stay Awake Window" to
                    "Building sleep drive for your next bedtime."
        }
    }
}
