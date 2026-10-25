package com.darkside3211.biphasicsleep.domain

import java.time.Duration
import java.time.ZonedDateTime

/**
 * The canonical states in the Weekly State Machine Architecture (v2.2 Event-Driven + Plain-English UI).
 */
enum class ProtocolStateId(
    val codeName: String,
    val title: String,
    val dayPhaseLabel: String,
    val modeTypeLabel: String,
    val scheduleWindowLabel: String,
    val durationLabel: String,
    val isWeekendMode: Boolean,
    val systemRulesSummary: String
) {
    STATE_WORK_CORE(
        codeName = "STATE_WORK_CORE",
        title = "Afternoon Workday Sleep",
        dayPhaseLabel = "Mon – Fri (Afternoon)",
        modeTypeLabel = "Full 3-Cycle Sleep (4h 44m)",
        scheduleWindowLabel = "Target: 12:30 – 17:14",
        durationLabel = "4h 44m (284m)",
        isWeekendMode = false,
        systemRulesSummary = "Main sleep before your 21:00 work shift. Wear dark sunglasses on your morning commute home (06:00–09:00) so bright daylight doesn't make it hard to fall asleep."
    ),
    STATE_WORK_NAP(
        codeName = "STATE_WORK_NAP",
        title = "20-Min Shift Power Nap",
        dayPhaseLabel = "Tue – Sat (During Shift)",
        modeTypeLabel = "20-Min Power Nap",
        scheduleWindowLabel = "Flexible Shift Break (~01:15)",
        durationLabel = "20m",
        isWeekendMode = false,
        systemRulesSummary = "Quick 20-minute nap during your shift break to clear brain fog before the 03:00 AM energy dip. Wait until after your nap (~02:00) to drink caffeine."
    ),
    STATE_BRIDGE_TO_WKND(
        codeName = "STATE_BRIDGE_TO_WKND",
        title = "Saturday Morning Post-Shift Sleep",
        dayPhaseLabel = "Saturday (Morning)",
        modeTypeLabel = "Short 2-Cycle Sleep (3h 14m)",
        scheduleWindowLabel = "Target: ~08:00 – ~11:14",
        durationLabel = "3h 14m (194m)",
        isWeekendMode = true,
        systemRulesSummary = "Shorter 3h 14m morning sleep after Friday night shift so you have the afternoon free AND are still tired enough to sleep Saturday night at 01:00 AM."
    ),
    STATE_WKND_CORE(
        codeName = "STATE_WKND_CORE",
        title = "Saturday Night Full Sleep",
        dayPhaseLabel = "Sat Night / Sun Early AM",
        modeTypeLabel = "Full 3-Cycle Sleep (4h 44m)",
        scheduleWindowLabel = "Target: 01:00 – 05:44",
        durationLabel = "4h 44m (284m)",
        isWeekendMode = true,
        systemRulesSummary = "Full 4h 44m night sleep starting around 01:00 AM so you get all of Sunday daytime awake without flipping your night-shift clock upside down."
    ),
    STATE_BRIDGE_TO_WORK(
        codeName = "STATE_BRIDGE_TO_WORK",
        title = "Sunday Night Reset Sleep",
        dayPhaseLabel = "Sun Night / Mon Early AM",
        modeTypeLabel = "Short 2-Cycle Sleep (3h 14m)",
        scheduleWindowLabel = "Target: 01:00 – 04:14",
        durationLabel = "3h 14m (194m)",
        isWeekendMode = false,
        systemRulesSummary = "Capped at 3h 14m (waking at 04:14 AM Monday). Staying awake from 04:14 AM to 12:30 PM (~8.25h) makes sure you're tired enough to fall asleep Monday afternoon before work."
    ),
    UNFULFILLED_ANCHOR_DELAYED(
        codeName = "UNFULFILLED_ANCHOR_DELAYED",
        title = "Late Sleep — Adjusted Duration",
        dayPhaseLabel = "Flexible Late Arrival / Plans",
        modeTypeLabel = "Dynamic Recovery Sleep",
        scheduleWindowLabel = "Dynamic Step-Down (3 -> 2 -> 1 Cycles)",
        durationLabel = "3h 14m / 1h 44m",
        isWeekendMode = false,
        systemRulesSummary = "You haven't logged sleep for this cycle yet. Automatically adjusts your sleep length so you don't oversleep 10 hours and pushes tonight's bedtime back up to +1h if needed."
    ),
    STATE_WAKE_LOCKOUT(
        codeName = "STATE_WAKE_LOCKOUT",
        title = "Stay Awake Until Next Sleep",
        dayPhaseLabel = "Awake Window (Between Sleeps)",
        modeTypeLabel = "Building Sleep Drive",
        scheduleWindowLabel = "Active after completing sleep",
        durationLabel = "Awake",
        isWeekendMode = false,
        systemRulesSummary = "Active after you finish your main sleep block (or when too close to your next bedtime). Staying awake now ensures you can fall asleep easily at your next target time."
    )
}

/**
 * The dynamic cycle alarm trigger actions in Module 1 (v2.2).
 */
enum class SleepBlockType(
    val ctaLabel: String,
    val shortTitle: String,
    val cyclesCount: Int,
    val formulaDescription: String,
    val requiresAntiBackslideByDefault: Boolean
) {
    CORE_3_CYCLES(
        ctaLabel = "Start 4h 44m Sleep",
        shortTitle = "3-Cycle Sleep (4h 44m)",
        cyclesCount = 3,
        formulaDescription = "3 × 90m cycles + 14m to fall asleep = 4h 44m (284m)",
        requiresAntiBackslideByDefault = true
    ),
    BRIDGE_2_CYCLES(
        ctaLabel = "Start 3h 14m Sleep",
        shortTitle = "2-Cycle Sleep (3h 14m)",
        cyclesCount = 2,
        formulaDescription = "2 × 90m cycles + 14m to fall asleep = 3h 14m (194m)",
        requiresAntiBackslideByDefault = true
    ),
    RECOVERY_1_CYCLE(
        ctaLabel = "Start 1h 44m Sleep",
        shortTitle = "1-Cycle Rescue (1h 44m)",
        cyclesCount = 1,
        formulaDescription = "1 × 90m cycle + 14m to fall asleep = 1h 44m (104m)",
        requiresAntiBackslideByDefault = true
    ),
    FLUSH_NAP_20M(
        ctaLabel = "Start 20m Power Nap",
        shortTitle = "20m Power Nap",
        cyclesCount = 0,
        formulaDescription = "20-minute light nap (wakes before deep sleep)",
        requiresAntiBackslideByDefault = true
    )
}

/**
 * Guardrail evaluation for each sleep trigger button in the current circadian phase (Module 2 & 3).
 */
enum class GuardrailPermission {
    PRIMARY_RECOMMENDED,
    ALLOWED_SECONDARY,
    WARN_INSOMNIA_OR_CIRCADIAN_RISK,
    LOCKED_ZERO_NAP_PROTOCOL
}

data class SleepActionOption(
    val blockType: SleepBlockType,
    val durationMinutes: Int,
    val calculatedWakeTime: ZonedDateTime,
    val calculatedTriggerMillis: Long,
    val permission: GuardrailPermission,
    val guardrailBadge: String,
    val guardrailReason: String
)

enum class AdvisorySeverity {
    INFO,
    PHYSIOLOGICAL_TRIGGER,
    HIGH_ALERT_LOCKOUT
}

data class PhysiologicalAdvisory(
    val title: String,
    val detail: String,
    val severity: AdvisorySeverity
)

/**
 * Weekend Anti-Nap / Boredom Lockout Timer status (Module 3).
 */
data class WeekendLockoutStatus(
    val isLockoutActive: Boolean,
    val isInEveningBoredomDangerZone: Boolean,
    val nextAnchorTimestamp: ZonedDateTime,
    val countdownUntilAnchor: Duration,
    val headline: String,
    val subtext: String
)

/**
 * Dynamic 20-Minute Nap Lockout Calculator models (Section 4.C & v2.2 Sliding Window).
 */
enum class NapLockoutRule {
    NONE_PERMITTED,
    NAP_ALREADY_COMPLETED_IN_PERIOD,
    PRE_ANCHOR_4_5H_CUTOFF,
    POST_WAKE_90M_COOLDOWN,
    WEEKEND_EVENING_DANGER_ZONE,
    DEVIATION_ZERO_NAP_LOCKOUT,
    OUTSIDE_PERMITTED_NAP_WINDOW
}

data class NapLockoutEvaluation(
    val isHardLocked: Boolean,
    val ruleTriggered: NapLockoutRule,
    val statusHeadline: String,
    val lockoutReason: String,
    val timeSinceLastWake: Duration?,
    val timeUntilNextAnchor: Duration,
    val nextAnchorTargetTime: ZonedDateTime,
    val calculatedWakeTime: ZonedDateTime,
    val isNapCompletedInCurrentPeriod: Boolean = false
)

/**
 * Primary Anchor Window status (v2.2 Event-Driven + Dynamic Cycle Truncation).
 */
data class PrimaryAnchorWindowStatus(
    val isInActiveLaunchWindow: Boolean,
    val activeStateId: ProtocolStateId,
    val permittedBlockType: SleepBlockType?,
    val permittedActionOption: SleepActionOption?,
    val nextScheduledAnchorState: ProtocolStateId,
    val nextScheduledAnchorTime: ZonedDateTime,
    val countdownToNextAnchor: Duration,
    val wakeLockoutHeadline: String,
    val wakeLockoutSubtext: String,
    val isUnfulfilledAnchorDelayed: Boolean = false,
    val isAnchorCompletedForCurrentCycle: Boolean = false,
    val nominalNextAnchorTime: ZonedDateTime = nextScheduledAnchorTime,
    val isAnchorPushedBack: Boolean = false,
    val delayedRecoveryHeadline: String? = null,
    val leewayAdvisoryText: String? = null,
    val availableCycleOptions: List<SleepActionOption> = emptyList()
)

/**
 * Full real-time evaluation of the user's current position in the Weekly State Machine (v2.2).
 */
data class PhaseEvaluation(
    val evaluatedAt: ZonedDateTime,
    val activeOrUpcomingState: ProtocolStateId,
    val phaseStatusHeadline: String,
    val phaseSubheadline: String,
    val isInsideShiftWindow: Boolean,
    val primaryRecommendedBlock: SleepBlockType,
    val actionOptions: List<SleepActionOption>,
    val primaryAnchorWindow: PrimaryAnchorWindowStatus,
    val napEvaluation: NapLockoutEvaluation,
    val weekendLockoutStatus: WeekendLockoutStatus,
    val advisories: List<PhysiologicalAdvisory>,
    val activeDeviationOverride: DeviationRecoveryPlan? = null
)
