package com.dsh.burnalarm.alarm

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dsh.burnalarm.BurnAlarmApp
import com.dsh.burnalarm.MainActivity
import com.dsh.burnalarm.R
import com.dsh.burnalarm.burn.BurnService
import com.dsh.burnalarm.data.AlarmRepository
import com.dsh.burnalarm.i18n.AppLocale.localized
import com.dsh.burnalarm.ring.RingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId < 0) return

        val appContext = context.applicationContext
        val repo = AlarmRepository.get(appContext)
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = repo.getAlarm(alarmId) ?: return@launch
                if (!alarm.enabled) return@launch

                // 1) 先发全屏通知 —— 这一步在广播接收器里永远被允许
                postFullScreenNotification(appContext, alarmId, alarm.label)

                // 2) 双保险：直接尝试拉起响铃页。
                //    由 setAlarmClock 触发的广播会给 App 一个短暂的后台启动 Activity 白名单窗口，
                //    所以真实闹钟场景下这一步通常能成功。
                //    万一被厂商 ROM 拦掉（后台启动被静默忽略，不抛异常），还有上面的全屏通知兜底。
                runCatching { appContext.startActivity(RingActivity.intent(appContext, alarmId)) }

                // 3) 再尝试拉起前台服务。Android 12+ 对后台启动前台服务有限制，
                //    精确闹钟属于豁免场景，但仍然用 try/catch 兜底：
                //    即使这里失败，Activity 一旦可见也能正常启动服务。
                runCatching { BurnService.startRinging(appContext, alarmId) }

                // 4) 重复闹钟排下一次；一次性闹钟自动关闭
                if (alarm.repeatDays.isNotEmpty()) {
                    AlarmScheduler.schedule(appContext, alarm)
                } else {
                    repo.setEnabled(alarmId, false)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun postFullScreenNotification(context: Context, alarmId: Long, label: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val fullScreen = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            RingActivity.intent(context, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ctx = context.localized()

        val notification = NotificationCompat.Builder(context, BurnAlarmApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(ctx.getString(R.string.notif_alarm_fired_title))
            .setContentText(
                if (label.isBlank()) ctx.getString(R.string.notif_alarm_fired_text) else label
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(BurnService.NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_FIRE = "com.dsh.burnalarm.action.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
