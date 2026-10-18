package com.darkside3211.biphasicsleep.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import com.darkside3211.biphasicsleep.domain.ProtocolStateId
import com.darkside3211.biphasicsleep.domain.SleepBlockType
import com.darkside3211.biphasicsleep.domain.SleepProtocolConfig
import com.darkside3211.biphasicsleep.ui.AlarmChallengeActivity
import com.darkside3211.biphasicsleep.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class ArmedAlarmInfo(
    val blockType: SleepBlockType,
    val stateId: ProtocolStateId,
    val armedAtMillis: Long,
    val triggerAtMillis: Long,
    val durationMinutes: Int,
    val requireStepChallenge: Boolean,
    val requiredSteps: Int,
    val requireBarcodeChallenge: Boolean,
    val registeredBarcode: String?,
    val enableFiveMinAntiBackslide: Boolean,
    val mirroredToSystemClock: Boolean
) {
    val targetWakeDateTime: ZonedDateTime
        get() = ZonedDateTime.ofInstant(Instant.ofEpochMilli(triggerAtMillis), ZoneId.systemDefault())
}

data class PendingAntiBackslideInfo(
    val sourceBlockTitle: String,
    val dismissedAtMillis: Long,
    val checkInTriggerMillis: Long,
    val delayMinutes: Int
) {
    val checkInDateTime: ZonedDateTime
        get() = ZonedDateTime.ofInstant(Instant.ofEpochMilli(checkInTriggerMillis), ZoneId.systemDefault())
}

/**
 * Module 1 & Module 4: Dynamic Cycle Alarm Calculator + Anti-Backslide Scheduler.
 *
 * Uses exact Doze-piercing AlarmManager.setAlarmClock() and optionally dispatches
 * AlarmClock.ACTION_SET_ALARM to the Android System Clock app.
 */
object HybridAlarmScheduler {

    const val ACTION_TRIGGER_ALARM = "com.darkside3211.biphasicsleep.ACTION_TRIGGER_ALARM"
    const val ACTION_ANTI_BACKSLIDE_CHECK = "com.darkside3211.biphasicsleep.ACTION_ANTI_BACKSLIDE_CHECK"
    const val ACTION_CONFIRM_AWAKE = "com.darkside3211.biphasicsleep.ACTION_CONFIRM_AWAKE"

    const val EXTRA_BLOCK_TYPE = "extra_block_type"
    const val EXTRA_STATE_ID = "extra_state_id"
    const val EXTRA_IS_ANTI_BACKSLIDE = "extra_is_anti_backslide"
    const val EXTRA_REQUIRE_STEPS = "extra_require_steps"
    const val EXTRA_REQUIRED_STEP_COUNT = "extra_required_step_count"
    const val EXTRA_REQUIRE_BARCODE = "extra_require_barcode"
    const val EXTRA_REGISTERED_BARCODE = "extra_registered_barcode"
    const val EXTRA_ENABLE_ANTI_BACKSLIDE = "extra_enable_anti_backslide"
    const val EXTRA_ANTI_BACKSLIDE_MINUTES = "extra_anti_backslide_minutes"

    private const val REQUEST_CODE_PRIMARY_ALARM = 1001
    private const val REQUEST_CODE_ANTI_BACKSLIDE = 1002

    private val _armedAlarm = MutableStateFlow<ArmedAlarmInfo?>(null)
    val armedAlarm: StateFlow<ArmedAlarmInfo?> = _armedAlarm.asStateFlow()

    private val _pendingAntiBackslide = MutableStateFlow<PendingAntiBackslideInfo?>(null)
    val pendingAntiBackslide: StateFlow<PendingAntiBackslideInfo?> = _pendingAntiBackslide.asStateFlow()

    fun scheduleSleepBlockAlarm(
        context: Context,
        blockType: SleepBlockType,
        stateId: ProtocolStateId,
        config: SleepProtocolConfig,
        nowMillis: Long = System.currentTimeMillis(),
        alsoSetSystemClockIntent: Boolean = config.mirrorToSystemClockApp
    ): ArmedAlarmInfo {
        val durationMin = config.durationMinutesFor(blockType)
        val triggerAtMillis = config.calculateTargetTriggerMillis(nowMillis, blockType)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
            putExtra(EXTRA_BLOCK_TYPE, blockType.name)
            putExtra(EXTRA_STATE_ID, stateId.name)
            putExtra(EXTRA_IS_ANTI_BACKSLIDE, false)
            putExtra(EXTRA_REQUIRE_STEPS, config.enableStepChallenge)
            putExtra(EXTRA_REQUIRED_STEP_COUNT, config.requiredSteps)
            putExtra(EXTRA_REQUIRE_BARCODE, config.enableBarcodeChallenge)
            putExtra(EXTRA_REGISTERED_BARCODE, config.registeredBarcodeValue)
            putExtra(EXTRA_ENABLE_ANTI_BACKSLIDE, config.enableFiveMinAntiBackslide)
            putExtra(EXTRA_ANTI_BACKSLIDE_MINUTES, config.antiBackslideDelayMinutes)
        }

        val operationPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PRIMARY_ALARM,
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_PRIMARY_ALARM,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
            alarmManager.setAlarmClock(clockInfo, operationPendingIntent)
        } catch (se: SecurityException) {
            // Fallback if exact alarm permission is revoked by user in OS settings
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operationPendingIntent
            )
        }

        var systemClockDispatched = false
        if (alsoSetSystemClockIntent) {
            systemClockDispatched = dispatchAndroidSystemClockIntent(
                context = context,
                triggerAtMillis = triggerAtMillis,
                label = "Biphasic ${stateId.codeName}: ${blockType.shortTitle}"
            )
        }

        val info = ArmedAlarmInfo(
            blockType = blockType,
            stateId = stateId,
            armedAtMillis = nowMillis,
            triggerAtMillis = triggerAtMillis,
            durationMinutes = durationMin,
            requireStepChallenge = config.enableStepChallenge,
            requiredSteps = config.requiredSteps,
            requireBarcodeChallenge = config.enableBarcodeChallenge,
            registeredBarcode = config.registeredBarcodeValue,
            enableFiveMinAntiBackslide = config.enableFiveMinAntiBackslide,
            mirroredToSystemClock = systemClockDispatched
        )
        _armedAlarm.value = info
        return info
    }

    /**
     * Fires AlarmClock.ACTION_SET_ALARM to configure the built-in Android Clock app.
     */
    fun dispatchAndroidSystemClockIntent(
        context: Context,
        triggerAtMillis: Long,
        label: String,
        skipUi: Boolean = true
    ): Boolean {
        return try {
            val targetTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(triggerAtMillis),
                ZoneId.systemDefault()
            )
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, targetTime.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, targetTime.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                putExtra(AlarmClock.EXTRA_VIBRATE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Module 4: Schedules the 5-minute Anti-Backslide secondary check-in after alarm dismissal.
     * If the user does not confirm they are still out of bed before checkInTriggerMillis,
     * the full alarm & challenge re-triggers to prevent the "Oct 17 Accidental Nap Trap".
     */
    fun scheduleAntiBackslideCheckIn(
        context: Context,
        sourceBlockTitle: String,
        delayMinutes: Int = 5,
        requireSteps: Boolean = true,
        requiredStepCount: Int = 15,
        requireBarcode: Boolean = false,
        registeredBarcode: String? = null
    ): PendingAntiBackslideInfo {
        val nowMillis = System.currentTimeMillis()
        val triggerMillis = nowMillis + (delayMinutes * 60_000L)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ANTI_BACKSLIDE_CHECK
            putExtra(EXTRA_BLOCK_TYPE, SleepBlockType.FLUSH_NAP_20M.name)
            putExtra(EXTRA_STATE_ID, ProtocolStateId.STATE_WORK_NAP.name)
            putExtra(EXTRA_IS_ANTI_BACKSLIDE, true)
            putExtra(EXTRA_REQUIRE_STEPS, requireSteps)
            putExtra(EXTRA_REQUIRED_STEP_COUNT, requiredStepCount)
            putExtra(EXTRA_REQUIRE_BARCODE, requireBarcode)
            putExtra(EXTRA_REGISTERED_BARCODE, registeredBarcode)
            putExtra(EXTRA_ENABLE_ANTI_BACKSLIDE, false)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ANTI_BACKSLIDE,
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_ANTI_BACKSLIDE,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent),
                pendingIntent
            )
        } catch (se: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }

        val info = PendingAntiBackslideInfo(
            sourceBlockTitle = sourceBlockTitle,
            dismissedAtMillis = nowMillis,
            checkInTriggerMillis = triggerMillis,
            delayMinutes = delayMinutes
        )
        _pendingAntiBackslide.value = info
        _armedAlarm.value = null
        return info
    }

    fun confirmAntiBackslideAwake(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ANTI_BACKSLIDE_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ANTI_BACKSLIDE,
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        _pendingAntiBackslide.value = null
    }

    fun cancelArmedAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PRIMARY_ALARM,
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        _armedAlarm.value = null
    }

    /**
     * Launches the full-screen AlarmChallengeActivity + RingingService immediately
     * so the user can test their Step Counter, Barcode Scanner, and 5-Minute Anti-Backslide settings.
     */
    fun triggerImmediateTestChallenge(
        context: Context,
        config: SleepProtocolConfig,
        isAntiBackslideCheck: Boolean = false
    ) {
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(EXTRA_BLOCK_TYPE, SleepBlockType.FLUSH_NAP_20M.name)
            putExtra(EXTRA_STATE_ID, ProtocolStateId.STATE_WORK_NAP.name)
            putExtra(EXTRA_IS_ANTI_BACKSLIDE, isAntiBackslideCheck)
            putExtra(EXTRA_REQUIRE_STEPS, config.enableStepChallenge)
            putExtra(EXTRA_REQUIRED_STEP_COUNT, config.requiredSteps)
            putExtra(EXTRA_REQUIRE_BARCODE, config.enableBarcodeChallenge)
            putExtra(EXTRA_REGISTERED_BARCODE, config.registeredBarcodeValue)
            putExtra(EXTRA_ENABLE_ANTI_BACKSLIDE, config.enableFiveMinAntiBackslide)
            putExtra(EXTRA_ANTI_BACKSLIDE_MINUTES, config.antiBackslideDelayMinutes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        val challengeIntent = Intent(context, AlarmChallengeActivity::class.java).apply {
            putExtras(serviceIntent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(challengeIntent)
    }
}
