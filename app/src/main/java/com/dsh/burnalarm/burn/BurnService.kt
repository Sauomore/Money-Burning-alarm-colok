package com.dsh.burnalarm.burn

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dsh.burnalarm.BurnAlarmApp
import com.dsh.burnalarm.R
import com.dsh.burnalarm.data.AlarmRepository
import com.dsh.burnalarm.data.AppSettings
import com.dsh.burnalarm.data.BurnMode
import com.dsh.burnalarm.data.MathDifficulty
import com.dsh.burnalarm.i18n.AppLocale.localized
import com.dsh.burnalarm.math.MathGenerator
import com.dsh.burnalarm.ring.RingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 响铃 + 焚烧的前台服务。
 *
 * 时间线完全由这里驱动：
 *   响铃 → 宽限倒计时 → 到点没答对 → 进入焚烧循环 → 直到用户答对或被跑飞保护拦下
 */
class BurnService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timelineJob: Job? = null
    private val llm by lazy { LlmClient(applicationContext) }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val repo by lazy { AlarmRepository.get(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        initTts()
        startVibration()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                // 必须在 5 秒内进入前台，所以先挂一个占位通知
                goForeground()
                if (alarmId >= 0) {
                    // 尽量把响铃页顶到前面。前台服务在「由精确闹钟触发的白名单窗口」内
                    // 或 App 本身可见时，这一步是允许的；被系统忽略也不影响响铃。
                    runCatching { startActivity(RingActivity.intent(this, alarmId)) }
                    startTimeline(alarmId)
                }
            }

            ACTION_STOP -> {
                shutdown(
                    BurnState.current.stopReason
                        ?: localized().getString(R.string.stop_solved)
                )
            }

            else -> {
                // 系统把服务重新拉起来了但没带 Intent：没有上下文可恢复。
                // 直接收工，避免留下一个「一直响着却停不下来」的前台服务。
                if (BurnState.current.phase == BurnState.Phase.IDLE) stopSelf()
            }
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------- 时间线

    private fun startTimeline(alarmId: Long) {
        timelineJob?.cancel()
        timelineJob = scope.launch {
            val alarm = repo.getAlarm(alarmId)
            val settings = repo.currentSettings()

            val problems = MathGenerator.generate(
                difficulty = alarm?.difficulty ?: MathDifficulty.NORMAL,
                count = alarm?.problemCount ?: 3
            )
            val grace = (alarm?.graceSeconds ?: 60).coerceIn(5, 600)

            BurnState.reset()
            BurnState.update {
                it.copy(
                    phase = BurnState.Phase.RINGING,
                    alarmId = alarmId,
                    alarmLabel = alarm?.label.orEmpty(),
                    problems = problems,
                    graceTotalSeconds = grace,
                    graceRemainingSeconds = grace,
                    apiConfigured = settings.isConfigured
                )
            }

            startRingtone()
            refreshNotification()
            val startedAt = System.currentTimeMillis()

            // 已用时计时器（响铃和焚烧全程都在跑）
            launch {
                while (isActive) {
                    val elapsed = (System.currentTimeMillis() - startedAt) / 1000
                    BurnState.update { it.copy(elapsedSeconds = elapsed) }
                    // 响铃阶段每秒刷新通知里的倒计时；焚烧阶段由焚烧循环自己刷新，
                    // 避免长时间焚烧时每秒都去戳一次通知
                    if (BurnState.current.phase == BurnState.Phase.RINGING) refreshNotification()
                    delay(1000)
                }
            }

            // 宽限倒计时
            for (remain in grace downTo 1) {
                if (!isActive) return@launch
                BurnState.update { it.copy(graceRemainingSeconds = remain) }
                delay(1000)
            }

            BurnState.update { it.copy(graceRemainingSeconds = 0, phase = BurnState.Phase.BURNING) }
            refreshNotification()

            if (!settings.isConfigured) {
                BurnState.update {
                    it.copy(lastError = localized().getString(R.string.stop_no_api))
                }
                refreshNotification()
                return@launch
            }

            burnLoop(settings, startedAt)
        }
    }

    private suspend fun burnLoop(settings: AppSettings, startedAt: Long) {
        var round = 0
        var calls = 0
        var promptTokens = 0L
        var completionTokens = 0L
        var cost = 0.0

        while (scope.isActive) {
            // ---- 跑飞保护：防的是代码缺陷导致的无限循环，不是防用户 ----
            val guard = settings.runawayGuardMinutes
            if (guard > 0) {
                val elapsedMinutes = (System.currentTimeMillis() - startedAt) / 60_000.0
                if (elapsedMinutes >= guard) {
                    shutdown(localized().getString(R.string.stop_guard, guard))
                    return
                }
            }

            round++
            val mode = when (settings.burnMode) {
                BurnMode.WASTE -> BurnMode.WASTE
                BurnMode.ROAST -> BurnMode.ROAST
                BurnMode.ALTERNATE -> if (round % 2 == 1) BurnMode.ROAST else BurnMode.WASTE
            }

            val system = if (mode == BurnMode.WASTE) llm.wasteSystem() else llm.roastSystem()
            val user = if (mode == BurnMode.WASTE) {
                llm.wasteUser(round)
            } else {
                llm.roastUser(round)
            }

            // 纯烧就把 max_tokens 拉满；毒鸡汤只要一句话，给 200 足够
            val maxTokens = if (mode == BurnMode.WASTE) settings.maxOutputTokens else 200

            val result = runCatching {
                llm.chat(settings, system, user, maxTokens)
            }

            result.onSuccess { r ->
                calls++
                promptTokens += r.promptTokens
                completionTokens += r.completionTokens
                cost = estimateCost(promptTokens, completionTokens, settings)

                val roast = if (mode == BurnMode.ROAST) r.content.trim() else ""
                if (roast.isNotEmpty()) speak(roast)

                BurnState.update {
                    it.copy(
                        calls = calls,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        estimatedCost = cost,
                        lastRoast = roast.ifEmpty { it.lastRoast },
                        lastError = null
                    )
                }
                refreshNotification()
            }.onFailure { e ->
                BurnState.update {
                    it.copy(lastError = e.message ?: e.javaClass.simpleName)
                }
                refreshNotification()
                // 出错时退避得久一点，避免把配额砸在重试上
                delay(5000)
            }

            delay(settings.burnIntervalSeconds.coerceIn(1, 120) * 1000L)
        }
    }

    private fun estimateCost(prompt: Long, completion: Long, s: AppSettings): Double =
        prompt / 1_000_000.0 * s.inputPricePerMillion +
            completion / 1_000_000.0 * s.outputPricePerMillion

    // ---------------------------------------------------------------- 声音 / 震动

    private fun startRingtone() {
        if (mediaPlayer != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        mediaPlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@BurnService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun stopRingtone() {
        runCatching {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        mediaPlayer = null
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        vibrator = v
        // 0 表示从 pattern 的第 0 个元素开始无限循环
        val pattern = longArrayOf(0, 900, 700, 900, 700)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
        }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun initTts() {
        runCatching {
            tts = TextToSpeech(applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) runCatching { tts?.language = Locale.CHINA }
            }
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "roast")
        }
    }

    private fun releaseTts() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    // ---------------------------------------------------------------- 通知

    private fun goForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun refreshNotification() {
        runCatching {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val s = BurnState.current

        val res = localized().resources

        val title = when (s.phase) {
            BurnState.Phase.RINGING ->
                res.getString(R.string.notif_ringing_title, s.graceRemainingSeconds)
            BurnState.Phase.BURNING -> res.getString(R.string.notif_burning_title)
            BurnState.Phase.FINISHED -> res.getString(R.string.notif_finished_title)
            BurnState.Phase.IDLE -> res.getString(R.string.notif_idle_title)
        }

        val text = when {
            s.phase == BurnState.Phase.RINGING ->
                res.getQuantityString(
                    R.plurals.notif_ringing_text,
                    s.problems.size,
                    s.problems.size
                )
            s.isBurning && s.lastError != null ->
                res.getString(R.string.notif_error_prefix, s.lastError)
            s.isBurning -> res.getString(
                R.string.notif_burn_stats,
                s.calls,
                s.totalTokens,
                String.format(Locale.US, "%.4f", s.estimatedCost)
            )
            else -> s.stopReason ?: res.getString(R.string.notif_waiting)
        }

        val fullScreen = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            RingActivity.intent(this, s.alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, BurnAlarmApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .build()
    }

    // ---------------------------------------------------------------- 收尾

    private fun shutdown(reason: String) {
        scope.launch {
            BurnState.update { it.copy(phase = BurnState.Phase.FINISHED, stopReason = reason) }
            refreshNotification()
            delay(350)
            stopRingtone()
            stopVibration()
            releaseTts()
            runCatching {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        isRunning = false
        timelineJob?.cancel()
        stopRingtone()
        stopVibration()
        releaseTts()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_RING = "com.dsh.burnalarm.action.RING"
        const val ACTION_STOP = "com.dsh.burnalarm.action.STOP_SERVICE"
        const val EXTRA_ALARM_ID = "alarm_id"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startRinging(context: Context, alarmId: Long) {
            val intent = Intent(context, BurnService::class.java).apply {
                action = ACTION_RING
                putExtra(EXTRA_ALARM_ID, alarmId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, BurnService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
        }
    }
}
