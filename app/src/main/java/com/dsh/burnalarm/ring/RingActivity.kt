package com.dsh.burnalarm.ring

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.burnalarm.R
import com.dsh.burnalarm.burn.BurnService
import com.dsh.burnalarm.burn.BurnState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 锁屏上直接显示并点亮屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)

        // 情况兜底：通知把我们拉起来了，但前台服务没起来（比如被系统拦了后台启动）
        if (!BurnService.isRunning && alarmId >= 0) {
            runCatching { BurnService.startRinging(this, alarmId) }
        }

        // 不允许返回键逃跑
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 故意什么都不做：想走就先把题做对
            }
        })

        setContent {
            MaterialTheme(colorScheme = RingColorScheme) {
                Surface(color = Color(0xFF0B0B12), modifier = Modifier.fillMaxSize()) {
                    RingScreen(onSolved = {
                        BurnService.stop(this)
                        finish()
                    })
                }
            }
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"

        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, RingActivity::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
            }
    }
}

private val RingColorScheme = darkColorScheme(
    primary = Color(0xFFFF6B35),
    onPrimary = Color(0xFF1A0B00),
    secondary = Color(0xFFFFD166),
    background = Color(0xFF0B0B12),
    surface = Color(0xFF15151F),
    onSurface = Color(0xFFEDEDF2),
    onSurfaceVariant = Color(0xFF9A9AAE),
    error = Color(0xFFFF5252)
)

@Composable
private fun RingScreen(onSolved: () -> Unit) {
    val status by BurnState.status.collectAsState()
    val problems = status.problems

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val answers = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) {
        mutableStateListOf<String>().apply { repeat(problems.size) { add("") } }
    }

    var wrongHint by remember { mutableStateOf(false) }

    val allCorrect = problems.isNotEmpty() &&
        problems.indices.all { i ->
            val v = answers.getOrNull(i)?.trim().orEmpty()
            v.isNotEmpty() && v.toIntOrNull() == problems[i].answer
        }

    val hasAnyWrong = problems.indices.any { i ->
        val v = answers.getOrNull(i)?.trim().orEmpty()
        v.isNotEmpty() && v.toIntOrNull() != problems[i].answer
    }

    LaunchedEffect(allCorrect) {
        if (allCorrect) {
            kotlinx.coroutines.delay(300)
            onSolved()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ---------------- 顶部：当前时间 ----------------
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFEDEDF2)
        )
        if (status.alarmLabel.isNotBlank()) {
            Text(
                text = status.alarmLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))

        // ---------------- 状态卡 ----------------
        if (status.isBurning) {
            BurningPanel(
                cost = status.estimatedCost,
                calls = status.calls,
                tokens = status.totalTokens,
                seconds = status.elapsedSeconds,
                error = status.lastError,
                roast = status.lastRoast
            )
        } else {
            RingingPanel(
                remaining = status.graceRemainingSeconds,
                total = status.graceTotalSeconds,
                apiConfigured = status.apiConfigured
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = pluralStringResource(
                R.plurals.ring_solve_count,
                problems.size,
                problems.size
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(16.dp))

        // ---------------- 题目 ----------------
        problems.forEachIndexed { index, problem ->
            val value = answers.getOrNull(index).orEmpty()
            val parsed = value.trim().toIntOrNull()
            val isWrong = value.trim().isNotEmpty() && parsed != problem.answer

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // 算式单独占一行：困难难度的 (38 + 24) × 7 这种长式子才不会折行
                Text(
                    text = "${problem.text} =",
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFEDEDF2)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() || it == '-' }.take(9)
                        if (index < answers.size) answers[index] = cleaned
                        wrongHint = false
                    },
                    singleLine = true,
                    isError = isWrong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (wrongHint && hasAnyWrong) {
            Text(
                text = stringResource(R.string.ring_wrong_answer),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { wrongHint = true },
            enabled = !allCorrect,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (allCorrect) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    stringResource(R.string.ring_submit),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ring_auto_release),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RingingPanel(remaining: Int, total: Int, apiConfigured: Boolean) {
    val urgent = remaining <= 15
    val color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF15151F), RoundedCornerShape(18.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (remaining > 0) "$remaining" else "0",
            fontSize = 58.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
        Text(
            text = stringResource(R.string.ring_countdown_suffix),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.ring_grace_total, total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!apiConfigured) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.ring_no_api_warn),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BurningPanel(
    cost: Double,
    calls: Int,
    tokens: Long,
    seconds: Long,
    error: String?,
    roast: String
) {
    val transition = rememberInfiniteTransition(label = "burn")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E0A0A), RoundedCornerShape(18.dp))
            .border(2.dp, Color(0xFFFF5252).copy(alpha = glow), RoundedCornerShape(18.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 26.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.ring_burning),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFF8A65),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = String.format(Locale.US, "¥%.4f", cost),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFFF5252).copy(alpha = 0.55f + 0.45f * glow)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(
                R.string.ring_burn_stats,
                calls,
                tokens,
                formatDuration(seconds)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!roast.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "「$roast」",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.9f)
            )
        }
    }
}

@Composable
private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) {
        stringResource(R.string.duration_min_sec, m, s)
    } else {
        stringResource(R.string.duration_sec, s)
    }
}
