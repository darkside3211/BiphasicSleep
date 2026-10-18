package com.antigravity.biphasicsleep.domain

import java.time.Duration
import java.time.ZonedDateTime

/**
 * The 5 canonical states in the Weekly State Machine Architecture (Section 2).
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
        title = "Workday Core Anchor",
        dayPhaseLabel = "Mon – Fri (Afternoon)",
        modeTypeLabel = "3-Cycle Core Anchor",
        scheduleWindowLabel = "12:30 – 17:14 (17:15)",
        durationLabel = "4h 44m (284m)",
        isWeekendMode = false,
        systemRulesSummary = "Primary workday sleep. Keep time-awake under 10h by 03:00. Wear dark sunglasses on commute home (06:00–09:00) to blunt daylight signaling."
    ),
    STATE_WORK_NAP(
        codeName = "STATE_WORK_NAP",
        title = "Mid-Shift Adenosine Flush",
        dayPhaseLabel = "Tue – Sat (Mid-Shift)",
        modeTypeLabel = "Adenosine Flush",
        scheduleWindowLabel = "01:15 – 01:35",
        durationLabel = "20m",
        isWeekendMode = false,
        systemRulesSummary = "Mandatory mid-shift nap to clear adenosine before the 03:00 circadian nadir. Delay sugar-free caffeine intake until 02:00."
    ),
    STATE_BRIDGE_TO_WKND(
        codeName = "STATE_BRIDGE_TO_WKND",
        title = "Saturday Morning Bridge #1",
        dayPhaseLabel = "Saturday (Morning)",
        modeTypeLabel = "2-Cycle Bridge #1",
        scheduleWindowLabel = "~08:00 – ~11:14",
        durationLabel = "3h 14m (194m)",
        isWeekendMode = true,
        systemRulesSummary = "Deliberately capped at 2 cycles (+14m latency). Leaves residual sleep pressure so the user can fall asleep at 01:00 Saturday night. Zero naps permitted all day."
    ),
    STATE_WKND_CORE(
        codeName = "STATE_WKND_CORE",
        title = "Weekend Recovery Core Block",
        dayPhaseLabel = "Sunday (Early AM / Sat Night)",
        modeTypeLabel = "3-Cycle Weekend Block",
        scheduleWindowLabel = "01:00 – 05:44",
        durationLabel = "4h 44m (284m)",
        isWeekendMode = true,
        systemRulesSummary = "Full 3-cycle recovery sleep anchored late at 01:00 to preserve night-shift rhythm while granting Sunday daytime wakefulness. Zero naps permitted all day Sunday."
    ),
    STATE_BRIDGE_TO_WORK(
        codeName = "STATE_BRIDGE_TO_WORK",
        title = "Monday Morning Bridge #2",
        dayPhaseLabel = "Monday (Early AM / Sun Night)",
        modeTypeLabel = "2-Cycle Bridge #2",
        scheduleWindowLabel = "01:00 – 04:14",
        durationLabel = "3h 14m (194m)",
        isWeekendMode = false,
        systemRulesSummary = "Deliberately capped at 2 cycles (+14m latency). Waking at 04:14 guarantees ~8.25 hours of wakefulness before 12:30, creating sufficient sleep pressure to lock back into STATE_WORK_CORE on Monday afternoon."
    )
}

/**
 * The 3 dynamic cycle alarm trigger actions in Module 1.
 */
enum class SleepBlockType(
    val ctaLabel: String,
    val shortTitle: String,
    val cyclesCount: Int,
    val formulaDescription: String,
    val requiresAntiBackslideByDefault: Boolean
) {
    CORE_3_CYCLES(
        ctaLabel = "Start 3-Cycle Core (+4h 44m)",
        shortTitle = "3-Cycle Core",
        cyclesCount = 3,
        formulaDescription = "3 × 90m cycles + 14m latency = 284 min",
        requiresAntiBackslideByDefault = false
    ),
    BRIDGE_2_CYCLES(
        ctaLabel = "Start 2-Cycle Bridge (+3h 14m)",
        shortTitle = "2-Cycle Bridge",
        cyclesCount = 2,
        formulaDescription = "2 × 90m cycles + 14m latency = 194 min",
        requiresAntiBackslideByDefault = true
    ),
    FLUSH_NAP_20M(
        ctaLabel = "Start 20m Flush Nap (+20m)",
        shortTitle = "20m Adenosine Flush",
        cyclesCount = 0,
        formulaDescription = "Strict 20m Stage 1/2 light sleep cap",
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
 * Full real-time evaluation of the user's current position in the Weekly State Machine.
 */
data class PhaseEvaluation(
    val evaluatedAt: ZonedDateTime,
    val activeOrUpcomingState: ProtocolStateId,
    val phaseStatusHeadline: String,
    val phaseSubheadline: String,
    val isInsideShiftWindow: Boolean,
    val primaryRecommendedBlock: SleepBlockType,
    val actionOptions: List<SleepActionOption>,
    val weekendLockoutStatus: WeekendLockoutStatus,
    val advisories: List<PhysiologicalAdvisory>,
    val activeDeviationOverride: DeviationRecoveryPlan? = null
)
