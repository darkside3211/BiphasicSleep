package com.darkside3211.biphasicsleep.domain

import java.time.Duration
import java.time.LocalTime

/**
 * Core Sleep Constants & Timing Parameters from Section 1 of the
 * Biphasic Sleep Anchoring Protocol (v1.0).
 *
 * Calculates alarm triggers using fixed 90-minute ultradian sleep cycles
 * plus a constant 14-minute sleep onset latency buffer:
 * - Core_Block_3_Cycles: 284 minutes (4h 44m = 3 * 90 + 14)
 * - Bridge_Block_2_Cycles: 194 minutes (3h 14m = 2 * 90 + 14)
 * - Adenosine_Flush_Nap: 20 minutes (Stage 1/2 light sleep cap)
 */
data class SleepProtocolConfig(
    val cycleDurationMinutes: Int = 90,
    val latencyBufferMinutes: Int = 14,
    val flushNapMinutes: Int = 20,
    val workCoreStart: LocalTime = LocalTime.of(12, 30),
    val workNapStart: LocalTime = LocalTime.of(1, 15),
    val satBridgeStart: LocalTime = LocalTime.of(8, 0),
    val wkndCoreStart: LocalTime = LocalTime.of(1, 0),
    val monBridgeStart: LocalTime = LocalTime.of(1, 0),
    val shiftStart: LocalTime = LocalTime.of(21, 0),
    val shiftEnd: LocalTime = LocalTime.of(6, 0),
    val sunglassesWindowStart: LocalTime = LocalTime.of(6, 0),
    val sunglassesWindowEnd: LocalTime = LocalTime.of(9, 0),
    val caffeinePermittedTime: LocalTime = LocalTime.of(2, 0),
    val circadianNadirTime: LocalTime = LocalTime.of(3, 0),
    val boredomZoneStart: LocalTime = LocalTime.of(18, 0),
    val boredomZoneEnd: LocalTime = LocalTime.of(23, 0),
    // Hybrid Alarm & Challenge Configuration
    val mirrorToSystemClockApp: Boolean = false,
    val enableFiveMinAntiBackslide: Boolean = true,
    val antiBackslideDelayMinutes: Int = 5,
    val enableStepChallenge: Boolean = true,
    val requiredSteps: Int = 15,
    val enableBarcodeChallenge: Boolean = false,
    val registeredBarcodeValue: String? = null
) {
    /** 3 * 90 + 14 = 284 minutes (4 hours 44 minutes) */
    val coreBlock3CyclesMinutes: Int
        get() = (3 * cycleDurationMinutes) + latencyBufferMinutes

    /** 2 * 90 + 14 = 194 minutes (3 hours 14 minutes) */
    val bridgeBlock2CyclesMinutes: Int
        get() = (2 * cycleDurationMinutes) + latencyBufferMinutes

    val coreBlock3CyclesDuration: Duration
        get() = Duration.ofMinutes(coreBlock3CyclesMinutes.toLong())

    val bridgeBlock2CyclesDuration: Duration
        get() = Duration.ofMinutes(bridgeBlock2CyclesMinutes.toLong())

    val flushNapDuration: Duration
        get() = Duration.ofMinutes(flushNapMinutes.toLong())

    val workCoreEnd: LocalTime
        get() = workCoreStart.plusMinutes(coreBlock3CyclesMinutes.toLong()) // 17:14

    val workNapEnd: LocalTime
        get() = workNapStart.plusMinutes(flushNapMinutes.toLong()) // 01:35

    val satBridgeEnd: LocalTime
        get() = satBridgeStart.plusMinutes(bridgeBlock2CyclesMinutes.toLong()) // 11:14

    val wkndCoreEnd: LocalTime
        get() = wkndCoreStart.plusMinutes(coreBlock3CyclesMinutes.toLong()) // 05:44

    val monBridgeEnd: LocalTime
        get() = monBridgeStart.plusMinutes(bridgeBlock2CyclesMinutes.toLong()) // 04:14

    fun durationMinutesFor(blockType: SleepBlockType): Int = when (blockType) {
        SleepBlockType.CORE_3_CYCLES -> coreBlock3CyclesMinutes
        SleepBlockType.BRIDGE_2_CYCLES -> bridgeBlock2CyclesMinutes
        SleepBlockType.FLUSH_NAP_20M -> flushNapMinutes
    }

    fun calculateTargetTriggerMillis(nowMillis: Long, blockType: SleepBlockType): Long {
        val durationMillis = durationMinutesFor(blockType) * 60_000L
        return nowMillis + durationMillis
    }
}
