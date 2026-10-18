package com.antigravity.biphasicsleep.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals(LocalTime.of(17, 14), config.workCoreEnd)
        assertEquals(LocalTime.of(1, 35), config.workNapEnd)
        assertEquals(LocalTime.of(11, 14), config.satBridgeEnd)
        assertEquals(LocalTime.of(5, 44), config.wkndCoreEnd)
        assertEquals(LocalTime.of(4, 14), config.monBridgeEnd)

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
    }

    @Test
    fun `verify weekly state machine transitions and mode guardrails`() {
        // 1. Monday 12:30 -> STATE_WORK_CORE
        val mon1230 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(12, 30), zone)
        val evalWorkCore = stateMachine.evaluate(mon1230)
        assertEquals(ProtocolStateId.STATE_WORK_CORE, evalWorkCore.activeOrUpcomingState)
        assertEquals(SleepBlockType.CORE_3_CYCLES, evalWorkCore.primaryRecommendedBlock)
        assertFalse(evalWorkCore.weekendLockoutStatus.isLockoutActive)

        // 2. Tuesday 01:15 -> STATE_WORK_NAP inside shift window
        val tue0115 = ZonedDateTime.of(LocalDate.of(2026, 10, 20), LocalTime.of(1, 15), zone)
        val evalWorkNap = stateMachine.evaluate(tue0115)
        assertEquals(ProtocolStateId.STATE_WORK_NAP, evalWorkNap.activeOrUpcomingState)
        assertTrue(evalWorkNap.isInsideShiftWindow)
        assertEquals(SleepBlockType.FLUSH_NAP_20M, evalWorkNap.primaryRecommendedBlock)
        assertTrue(evalWorkNap.advisories.any { it.title.contains("Caffeine Gate") })

        // 3. Wednesday 07:30 -> Commute Sunglasses Trigger (06:00-09:00)
        val wed0730 = ZonedDateTime.of(LocalDate.of(2026, 10, 21), LocalTime.of(7, 30), zone)
        val evalCommute = stateMachine.evaluate(wed0730)
        assertTrue(evalCommute.advisories.any { it.title.contains("Sunglasses") || it.title.contains("Daylight Blunting") })

        // 4. Saturday 08:00 -> STATE_BRIDGE_TO_WKND (2-cycle cap)
        val sat0800 = ZonedDateTime.of(LocalDate.of(2026, 10, 17), LocalTime.of(8, 0), zone)
        val evalSatBridge = stateMachine.evaluate(sat0800)
        assertEquals(ProtocolStateId.STATE_BRIDGE_TO_WKND, evalSatBridge.activeOrUpcomingState)
        assertEquals(SleepBlockType.BRIDGE_2_CYCLES, evalSatBridge.primaryRecommendedBlock)

        // 5. Saturday 20:00 -> Weekend Boredom Danger Zone Lockout (18:00-23:00)
        val sat2000 = ZonedDateTime.of(LocalDate.of(2026, 10, 17), LocalTime.of(20, 0), zone)
        val evalSatEvening = stateMachine.evaluate(sat2000)
        assertTrue(evalSatEvening.weekendLockoutStatus.isLockoutActive)
        assertTrue(evalSatEvening.weekendLockoutStatus.isInEveningBoredomDangerZone)
        assertEquals(300L, evalSatEvening.weekendLockoutStatus.countdownUntilAnchor.toMinutes()) // 5h until 01:00
        val flushOptionSat = evalSatEvening.actionOptions.first { it.blockType == SleepBlockType.FLUSH_NAP_20M }
        assertEquals(GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL, flushOptionSat.permission)

        // 6. Monday 01:00 -> STATE_BRIDGE_TO_WORK (Locked to 2-Cycle Bridge, warns against 3 cycles)
        val mon0100 = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(1, 0), zone)
        val evalMonBridge = stateMachine.evaluate(mon0100)
        assertEquals(ProtocolStateId.STATE_BRIDGE_TO_WORK, evalMonBridge.activeOrUpcomingState)
        assertEquals(SleepBlockType.BRIDGE_2_CYCLES, evalMonBridge.primaryRecommendedBlock)
        val core3OnMonNight = evalMonBridge.actionOptions.first { it.blockType == SleepBlockType.CORE_3_CYCLES }
        assertEquals(GuardrailPermission.WARN_INSOMNIA_OR_CIRCADIAN_RISK, core3OnMonNight.permission)
        val bridge2OnMonNight = evalMonBridge.actionOptions.first { it.blockType == SleepBlockType.BRIDGE_2_CYCLES }
        assertEquals(GuardrailPermission.PRIMARY_RECOMMENDED, bridge2OnMonNight.permission)
        assertEquals(LocalTime.of(4, 14), bridge2OnMonNight.calculatedWakeTime.toLocalTime())
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
        val presets = com.antigravity.biphasicsleep.ui.SimulationPreset.entries
        assertEquals(8, presets.size)
        presets.forEach { preset ->
            val dt = preset.simulatedDateTime ?: ZonedDateTime.now(zone)
            val evaluation = stateMachine.evaluate(dt)
            assertEquals(3, evaluation.actionOptions.size)
        }
    }
}
