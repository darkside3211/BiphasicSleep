package com.darkside3211.biphasicsleep.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HybridAlarmScheduler.ACTION_CONFIRM_AWAKE -> {
                HybridAlarmScheduler.confirmAntiBackslideAwake(context)
                AlarmRingingService.stop(context)
            }

            HybridAlarmScheduler.ACTION_TRIGGER_ALARM,
            HybridAlarmScheduler.ACTION_ANTI_BACKSLIDE_CHECK -> {
                val isAntiBackslide =
                    intent.action == HybridAlarmScheduler.ACTION_ANTI_BACKSLIDE_CHECK ||
                        intent.getBooleanExtra(HybridAlarmScheduler.EXTRA_IS_ANTI_BACKSLIDE, false)

                val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                    putExtras(intent)
                    putExtra(HybridAlarmScheduler.EXTRA_IS_ANTI_BACKSLIDE, isAntiBackslide)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
