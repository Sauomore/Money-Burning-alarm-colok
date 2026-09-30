package com.dsh.burnalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dsh.burnalarm.data.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机 / 应用更新后重新排闹钟。
 * AlarmManager 里排的东西在重启后会全部丢失，不重排的话闹钟就废了。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val appContext = context.applicationContext
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val alarms = AlarmRepository.get(appContext).currentAlarms()
                        AlarmScheduler.rescheduleAll(appContext, alarms)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
