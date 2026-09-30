package com.dsh.burnalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dsh.burnalarm.MainActivity
import com.dsh.burnalarm.data.Alarm
import java.util.Calendar

/**
 * 用 AlarmManager.setAlarmClock() 排闹钟。
 *
 * 选 setAlarmClock 而不是 setExactAndAllowWhileIdle 的原因：
 *  - 系统会显示闹钟图标，属于「用户可见的闹钟」，最不容易被厂商 ROM 杀掉
 *  - 不受 Doze 的批次延迟影响
 *  - 在 Android 12+ 的后台启动前台服务限制里享有豁免
 */
object AlarmScheduler {

    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.enabled) {
            cancel(context, alarm)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerAt(alarm)
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent(context, alarm)),
            operation(context, alarm)
        )
    }

    fun cancel(context: Context, alarm: Alarm) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(operation(context, alarm))
    }

    fun rescheduleAll(context: Context, alarms: List<Alarm>) {
        alarms.forEach { if (it.enabled) schedule(context, it) else cancel(context, it) }
    }

    /** 下一次应该响的时刻（毫秒时间戳） */
    fun nextTriggerAt(alarm: Alarm, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()

        if (alarm.repeatDays.isEmpty()) {
            cal.timeInMillis = from
            cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
            cal.set(Calendar.MINUTE, alarm.minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }

        for (offset in 0..7) {
            cal.timeInMillis = from
            cal.add(Calendar.DAY_OF_YEAR, offset)
            cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
            cal.set(Calendar.MINUTE, alarm.minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= from) continue
            if (isoDayOfWeek(cal) in alarm.repeatDays) return cal.timeInMillis
        }

        // 理论上到不了这里；兜底为 24 小时后
        cal.timeInMillis = from + 24 * 60 * 60 * 1000L
        return cal.timeInMillis
    }

    /** Calendar.DAY_OF_WEEK（1=周日）转 ISO（1=周一 … 7=周日） */
    private fun isoDayOfWeek(cal: Calendar): Int {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    private fun operation(context: Context, alarm: Alarm): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = android.net.Uri.parse("burnalarm://alarm/${alarm.id}")
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 点击系统闹钟图标时打开 App */
    private fun showIntent(context: Context, alarm: Alarm): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            (alarm.id + 9000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }
}
