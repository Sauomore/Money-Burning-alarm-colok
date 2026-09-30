package com.dsh.burnalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.burnalarm.R
import com.dsh.burnalarm.burn.BurnService
import com.dsh.burnalarm.data.Alarm
import com.dsh.burnalarm.data.MathDifficulty
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    viewModel: AlarmViewModel,
    alarmId: Long,
    onDone: () -> Unit
) {
    val alarms by viewModel.alarms.collectAsState()
    val context = LocalContext.current
    val dayNames = stringArrayResource(R.array.day_short)

    var loaded by remember { mutableStateOf(alarmId <= 0L) }

    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(0) }
    var label by remember { mutableStateOf("") }
    var repeatDays by remember { mutableStateOf(emptySet<Int>()) }
    var difficulty by remember { mutableStateOf(MathDifficulty.NORMAL) }
    var problemCount by remember { mutableIntStateOf(3) }
    var graceSeconds by remember { mutableIntStateOf(60) }

    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(alarms, alarmId) {
        if (!loaded) {
            alarms.firstOrNull { it.id == alarmId }?.let { a ->
                hour = a.hour
                minute = a.minute
                label = a.label
                repeatDays = a.repeatDays
                difficulty = a.difficulty
                problemCount = a.problemCount
                graceSeconds = a.graceSeconds
                loaded = true
            }
        }
    }

    fun buildAlarm(id: Long) = Alarm(
        id = id,
        hour = hour,
        minute = minute,
        enabled = true,
        repeatDays = repeatDays,
        label = label.trim(),
        difficulty = difficulty,
        problemCount = problemCount,
        graceSeconds = graceSeconds
    )

    fun persist(): Long {
        val id = if (alarmId > 0L) alarmId else System.currentTimeMillis()
        viewModel.upsert(buildAlarm(id))
        return id
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (alarmId > 0L) R.string.edit_title_edit else R.string.edit_title_new
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (alarmId > 0L) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.common_delete)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(12.dp))

            // -------- 时间 --------
            Text(
                text = String.format(Locale.US, "%02d:%02d", hour, minute),
                fontSize = 68.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            OutlinedButton(onClick = { showTimePicker = true }) {
                Text(stringResource(R.string.edit_change_time))
            }

            Spacer(Modifier.height(24.dp))

            // -------- 重复 --------
            SectionTitle(stringResource(R.string.edit_repeat))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                dayNames.forEachIndexed { index, name ->
                    val iso = index + 1
                    FilterChip(
                        selected = iso in repeatDays,
                        onClick = {
                            repeatDays = if (iso in repeatDays) repeatDays - iso else repeatDays + iso
                        },
                        label = { Text(name) }
                    )
                }
            }
            Text(
                text = if (repeatDays.isEmpty()) {
                    stringResource(R.string.edit_repeat_none_hint)
                } else {
                    pluralStringResource(
                        R.plurals.repeat_selected_days,
                        repeatDays.size,
                        repeatDays.size
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )

            Spacer(Modifier.height(20.dp))

            // -------- 备注 --------
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(30) },
                label = { Text(stringResource(R.string.edit_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // -------- 难度 --------
            SectionTitle(stringResource(R.string.edit_difficulty))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MathDifficulty.entries.forEach { d ->
                    FilterChip(
                        selected = difficulty == d,
                        onClick = { difficulty = d },
                        label = { Text(d.label()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // -------- 题量 --------
            SectionTitle(stringResource(R.string.edit_problem_count, problemCount))
            Slider(
                value = problemCount.toFloat(),
                onValueChange = { problemCount = it.roundToInt() },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // -------- 宽限 --------
            SectionTitle(stringResource(R.string.edit_grace, graceSeconds))
            Text(
                text = stringResource(R.string.edit_grace_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = graceSeconds.toFloat(),
                onValueChange = { graceSeconds = it.roundToInt() },
                valueRange = 10f..300f,
                steps = 28,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = { persist(); onDone() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    stringResource(R.string.edit_save),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            // 立刻验证一次，不用等到明天早上
            OutlinedButton(
                onClick = {
                    val id = persist()
                    // 稍等一下让数据落盘，再拉响
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        runCatching { BurnService.startRinging(context, id) }
                    }, 400)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.edit_test_now))
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showTimePicker) {
        val state = key(hour, minute) {
            rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    showTimePicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            text = { TimePicker(state = state) }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.edit_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(alarmId)
                    showDeleteConfirm = false
                    onDone()
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    )
}
