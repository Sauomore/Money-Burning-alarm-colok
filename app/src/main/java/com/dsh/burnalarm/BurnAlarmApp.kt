package com.dsh.burnalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import com.dsh.burnalarm.i18n.AppLocale.localized

class BurnAlarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    /**
     * 创建/刷新通知渠道。
     *
     * 做成幂等的，是因为渠道名字在换语言后需要重写一遍才会更新；
     * MainActivity 每次启动都会再调一次（传进来的是本地化后的 Context）。
     */
    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ctx = context.localized()

        // 响铃通道：最高优先级 + 允许全屏 intent 覆盖锁屏
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            ctx.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ctx.getString(R.string.channel_alarm_desc)
            setShowBadge(true)
            enableVibration(true)
            // 铃声由 BurnService 自己用 MediaPlayer 播（USAGE_ALARM），渠道这里置空
            setSound(
                null,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        // 焚烧通道：常驻低打扰通知，用来显示当前烧了多少钱
        val burn = NotificationChannel(
            CHANNEL_BURN,
            ctx.getString(R.string.channel_burn_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = ctx.getString(R.string.channel_burn_desc)
            setShowBadge(false)
        }

        nm.createNotificationChannels(listOf(alarm, burn))
    }

    companion object {
        const val CHANNEL_ALARM = "alarm_ring"
        const val CHANNEL_BURN = "token_burn"
    }
}
