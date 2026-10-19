package com.darkside3211.biphasicsleep.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class BiphasicSleepProtocolTest {

    private val zone = ZoneId.of("Asia/Manila")
    private val config = SleepProtocolConfig()
    private val stateMachine = CircadianStateMachine(config)
    private val deviationCalculator = DeviationRecoveryCalculator(config)

    @Test
    fun `verify core sleep constants and timing formulas`() {
        assertEquals(90, config.cycleDurationMinutes)
        assertEquals(14, config.latencyBufferMinutes)
        assertEquals(284, config.coreBlock3CyclesMinutes) // 3 * 90 + 14 = 284m (4h 44m)
        assertEquals(194, config.bridgeBlock2CyclesMinutes) // 2 * 90 + 14 = 194m (3h 14m)
        assertEquals(20, config.flushNapMinutes)
        assertEquals(270, config.preAnchorNapCutoffMinutes) // 4.5 hours
        assertEquals(90, config.postWakeCooldownMinutes) // 90 minutes anti-backslide cooldown

        assertEquals(LocalTime.of(17, 14), config.workCoreEnd)
        assertEquals(LocalTime.of(1, 35), config.workNapEnd)
        assertEquals(LocalTime.of(11, 14), config.satBridgeEnd)
        assertEquals(LocalTime.of(5, 44), config.wkndCoreEnd)
        assertEquals(LocalTime.of(4, 14), config.monBridgeEnd)

        // Launch windows
        assertEquals(LocalTime.of(11, 30), config.workCoreLaunchStart)
        assertEquals(LocalTime.of(14, 0), config.workCoreLaunchEnd)
        assertEquals(LocalTime.of(6, 30), config.satBridgeLaunchStart)
        assertEquals(LocalTime.of(9, 30), config.satBridgeLaunchEnd)
        assertEquals(LocalTime.of(0, 0), config.wkndCoreLaunchStart)
        assertEquals(LocalTime.of(2, 30), config.wkndCoreLaunchEnd)
        assertEquals(LocalTime.of(0, 0), config.monBridgeLaunchStart)
        assertEquals(LocalTime.of(2, 30), config.monBridgeLaunchEnd)
        assertEquals(LocalTime.of(1, 0), config.workNapLaunchStart)
        assertEquals(LocalTime.of(2, 0), config.workNapLaunchEnd)

        val baseMillis = 1_700_000_000_000L
        assertEquals(
            baseMillis + 284 * 60_000L,
            config.calculateTargetTriggerMillis(baseMillis, SleepBlockType.CORE_3_CYCLES)
        )
        assertEquals(
            baseMillis + 194 * 60_000L,
            config.calculateTargetTriggerMillis(baseMillis, SleepBlockType.BRIDGE_2_CYCLES)
        )
        assertEquals(
            baseMillis + 20 * 60_000L,
            config.calculateTargetTriggerMillis(baseMillis, SleepBlockType.FLUSH_NAP_20M)
        )

        // Anti-Backslide check verification
        SleepBlockType.entries.forEach {
            assertTrue("Expected anti-backslide enabled for $it", it.requiresAntiBackslideByDefault)
        }
    }

    @Test
    fun `verify single-action launch window enforcement`() {
        // Mon 12:30 -> Inside Work Core Window (11:30–14:00): Only 3-Cycle Core (+284m)
        val mon1230 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(12, 30), zone)
        val evalWorkCore = stateMachine.evaluate(mon1230)
        assertTrue(evalWorkCore.primaryAnchorWindow.isInActiveLaunchWindow)
        assertEquals(ProtocolStateId.STATE_WORK_CORE, evalWorkCore.primaryAnchorWindow.activeStateId)
        assertEquals(SleepBlockType.CORE_3_CYCLES, evalWorkCore.primaryAnchorWindow.permittedBlockType)
        assertNotNull(evalWorkCore.primaryAnchorWindow.permittedActionOption)
        assertEquals(SleepBlockType.CORE_3_CYCLES, evalWorkCore.primaryAnchorWindow.permittedActionOption?.blockType)
        assertEquals(GuardrailPermission.PRIMARY_RECOMMENDED, evalWorkCore.primaryAnchorWindow.permittedActionOption?.permission)

        // Sat 08:00 -> Inside Sat Bridge Window (06:30–09:30): Only 2-Cycle Bridge (+194m)
        val sat0800 = ZonedDateTime.of(LocalDate.of(2026, 10, 17), LocalTime.of(8, 0), zone)
        val evalSatBridge = stateMachine.evaluate(sat0800)
        assertTrue(evalSatBridge.primaryAnchorWindow.isInActiveLaunchWindow)
        assertEquals(ProtocolStateId.STATE_BRIDGE_TO_WKND, evalSatBridge.primaryAnchorWindow.activeStateId)
        assertEquals(SleepBlockType.BRIDGE_2_CYCLES, evalSatBridge.primaryAnchorWindow.permittedBlockType)
        assertNotNull(evalSatBridge.primaryAnchorWindow.permittedActionOption)
        assertEquals(SleepBlockType.BRIDGE_2_CYCLES, evalSatBridge.primaryAnchorWindow.permittedActionOption?.blockType)

        // Sun 01:00 -> Inside Weekend Core Window (00:00–02:30): Only 3-Cycle Core (+284m)
        val sun0100 = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(1, 0), zone)
        val evalSunCore = stateMachine.evaluate(sun0100)
        assertTrue(evalSunCore.primaryAnchorWindow.isInActiveLaunchWindow)
        assertEquals(ProtocolStateId.STATE_WKND_CORE, evalSunCore.primaryAnchorWindow.activeStateId)
        assertEquals(SleepBlockType.CORE_3_CYCLES, evalSunCore.primaryAnchorWindow.permittedBlockType)

        // Mon 01:00 -> Inside Bridge to Work Window (00:00–02:30): Only 2-Cycle Bridge (+194m)
        val mon0100 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(1, 0), zone)
        val evalMonBridge = stateMachine.evaluate(mon0100)
        assertTrue(evalMonBridge.primaryAnchorWindow.isInActiveLaunchWindow)
        assertEquals(ProtocolStateId.STATE_BRIDGE_TO_WORK, evalMonBridge.primaryAnchorWindow.activeStateId)
        assertEquals(SleepBlockType.BRIDGE_2_CYCLES, evalMonBridge.primaryAnchorWindow.permittedBlockType)
    }

    @Test
    fun `verify Incident 2 - Active Wake Lockout State outside launch windows`() {
        // Monday 06:05 -> Post-Bridge Wake Window (04:14 to 11:30)
        // Hard-locks primary CTA into STATE_WAKE_LOCKOUT countdown card
        val mon0605 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(6, 5), zone)
        val evalMon0605 = stateMachine.evaluate(mon0605)

        assertFalse(evalMon0605.primaryAnchorWindow.isInActiveLaunchWindow)
        assertEquals(ProtocolStateId.STATE_WAKE_LOCKOUT, evalMon0605.activeOrUpcomingState)
        assertNull(evalMon0605.primaryAnchorWindow.permittedBlockType)
        assertNull(evalMon0605.primaryAnchorWindow.permittedActionOption)

        assertEquals(ProtocolStateId.STATE_WORK_CORE, evalMon0605.primaryAnchorWindow.nextScheduledAnchorState)
        assertEquals(385L, evalMon0605.primaryAnchorWindow.countdownToNextAnchor.toMinutes()) // 6h 25m to 12:30
        assertTrue(evalMon0605.primaryAnchorWindow.wakeLockoutHeadline.contains("WAKE LOCKOUT ACTIVE"))
        assertTrue(evalMon0605.primaryAnchorWindow.wakeLockoutSubtext.contains("12:30"))
        assertTrue(evalMon0605.primaryAnchorWindow.wakeLockoutSubtext.contains("6h 25m"))
    }

    @Test
    fun `verify Dynamic Nap Lockout Rule 1 - Pre-Anchor 4_5h Cutoff`() {
        // Monday 08:30 -> Next anchor is 12:30 (4h / 240m away < 270m cutoff)
        val mon0830 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(8, 30), zone)
        val wake0414 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(4, 14), zone)
        val napEval = stateMachine.evaluateNapLockout(mon0830, wake0414)

        assertTrue(napEval.isHardLocked)
        assertEquals(NapLockoutRule.PRE_ANCHOR_4_5H_CUTOFF, napEval.ruleTriggered)
        assertTrue(napEval.lockoutReason.contains("< 4.5h until"))
        assertTrue(napEval.lockoutReason.contains("Napping now will bleed off required sleep pressure"))
    }

    @Test
    fun `verify Dynamic Nap Lockout Rule 2 - Post-Wake 90m Anti-Backslide Cooldown`() {
        // Monday 04:30 -> Last wake was 04:14 (16m since wake < 90m cooldown)
        val mon0430 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(4, 30), zone)
        val wake0414 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(4, 14), zone)
        val napEval = stateMachine.evaluateNapLockout(mon0430, wake0414)

        assertTrue(napEval.isHardLocked)
        assertEquals(NapLockoutRule.POST_WAKE_90M_COOLDOWN, napEval.ruleTriggered)
        assertTrue(napEval.lockoutReason.contains("Post-Wake Anti-Backslide Cooldown (90m)"))
        assertTrue(napEval.lockoutReason.contains("Get out of bed to anchor wakefulness"))
    }

    @Test
    fun `verify Dynamic Nap Lockout Rule 3 - Weekend Evening Danger Zone`() {
        // Saturday 20:00 (Incident #1 trigger) -> Inside 18:00–01:00 Weekend Danger Zone
        val sat2000 = ZonedDateTime.of(LocalDate.of(2026, 10, 17), LocalTime.of(20, 0), zone)
        val napEvalSat = stateMachine.evaluateNapLockout(sat2000, null)

        assertTrue(napEvalSat.isHardLocked)
        assertEquals(NapLockoutRule.WEEKEND_EVENING_DANGER_ZONE, napEvalSat.ruleTriggered)
        assertTrue(napEvalSat.lockoutReason.contains("Weekend Evening Danger Zone"))

        // Sunday 19:30 -> Inside 18:00–01:00 Weekend Danger Zone before Monday 01:00
        val sun1930 = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(19, 30), zone)
        val napEvalSun = stateMachine.evaluateNapLockout(sun1930, null)

        assertTrue(napEvalSun.isHardLocked)
        assertEquals(NapLockoutRule.WEEKEND_EVENING_DANGER_ZONE, napEvalSun.ruleTriggered)
    }

    @Test
    fun `verify Dynamic Nap Lockout Rule 4 - Permitted Windows`() {
        // 1. Tuesday 01:15 -> Permitted Mid-Shift Nap Window (01:00–02:00)
        val tue0115 = ZonedDateTime.of(LocalDate.of(2026, 10, 20), LocalTime.of(1, 15), zone)
        val napMidShift = stateMachine.evaluateNapLockout(tue0115, null)

        assertFalse(napMidShift.isHardLocked)
        assertEquals(NapLockoutRule.NONE_PERMITTED, napMidShift.ruleTriggered)
        assertTrue(napMidShift.statusHeadline.contains("MID-SHIFT"))

        // 2. Monday 06:05 -> Permitted Daytime Wake Stretch (>90m post wake & >4.5h pre anchor)
        val mon0605 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(6, 5), zone)
        val wake0414 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(4, 14), zone)
        val napDaytime = stateMachine.evaluateNapLockout(mon0605, wake0414)

        assertFalse(napDaytime.isHardLocked)
        assertEquals(NapLockoutRule.NONE_PERMITTED, napDaytime.ruleTriggered)
        assertTrue(napDaytime.statusHeadline.contains("DAYTIME"))
    }

    @Test
    fun `verify Oct 17-18 2026 Accidental Nap Trap incident log and recovery protocol`() {
        val incidentInput = DeviationRecoveryCalculator.createOct17To18IncidentInput(zone)
        val plan = deviationCalculator.evaluateDeviation(incidentInput)

        // Slept 20:00 Sat Oct 17 -> Woke 04:27 Sun Oct 18 = 8h 27m (507 minutes) = 5 cycles + latency
        assertEquals(507L, plan.actualSleepDuration.toMinutes())
        assertEquals(5, plan.estimatedCyclesCompleted)

        // Baseline wake was 05:44 Sun -> Delta is 1h 17m (77 minutes)
        assertEquals(LocalTime.of(5, 44), plan.targetWakeTimeBaseline.toLocalTime())
        assertEquals(77L, plan.wakeWindowDeltaFromBaseline.toMinutes())

        // Next anchor is Monday Oct 19 @ 01:00 (STATE_BRIDGE_TO_WORK)
        assertEquals(ProtocolStateId.STATE_BRIDGE_TO_WORK, plan.nextAnchorState)
        assertEquals(
            ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(1, 0), zone),
            plan.nextAnchorTimestamp
        )

        // Adjusted Sunday wake window: 04:27 Sun to 01:00 Mon = 20h 33m (1233 minutes >= 16h)
        assertEquals(20 * 60 + 33L, plan.timeUntilNextAnchor.toMinutes())
        assertTrue(plan.enforceZeroNapLockout)

        // Step 2: Monday 01:00 block must be capped to 2 cycles (01:00 - 04:14), leaving 8h 16m before 12:30
        assertTrue(plan.capPrecedingOvernightToTwoCycles)
        assertEquals(8 * 60 + 16L, plan.postOvernightWakeToWorkCoreDuration?.toMinutes())
        assertEquals(4, plan.executedRecoverySteps.size)
    }

    @Test
    fun `verify all simulation presets initialize and evaluate without error`() {
        val presets = com.darkside3211.biphasicsleep.ui.SimulationPreset.entries
        assertEquals(11, presets.size)
        presets.forEach { preset ->
            val dt = preset.simulatedDateTime ?: ZonedDateTime.now(zone)
            val evaluation = stateMachine.evaluate(
                now = dt,
                lastActualWakeTime = preset.simulatedLastWakeTime
            )
            assertEquals(3, evaluation.actionOptions.size)
        }
    }
}
