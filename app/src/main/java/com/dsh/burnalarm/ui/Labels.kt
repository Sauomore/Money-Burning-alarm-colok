package com.dsh.burnalarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.dsh.burnalarm.R
import com.dsh.burnalarm.data.Alarm
import com.dsh.burnalarm.data.BurnMode
import com.dsh.burnalarm.data.MathDifficulty

/**
 * 数据层不带任何文案，显示名统一在这里按当前语言解析。
 * （枚举上挂死中文会让 i18n 变成不可能完成的任务）
 */

@Composable
fun MathDifficulty.label(): String = stringResource(
    when (this) {
        MathDifficulty.EASY -> R.string.difficulty_easy
        MathDifficulty.NORMAL -> R.string.difficulty_normal
        MathDifficulty.HARD -> R.string.difficulty_hard
    }
)

@Composable
fun BurnMode.label(): String = stringResource(
    when (this) {
        BurnMode.WASTE -> R.string.burn_mode_waste
        BurnMode.ROAST -> R.string.burn_mode_roast
        BurnMode.ALTERNATE -> R.string.burn_mode_alternate
    }
)

@Composable
fun BurnMode.description(): String = stringResource(
    when (this) {
        BurnMode.WASTE -> R.string.burn_mode_waste_desc
        BurnMode.ROAST -> R.string.burn_mode_roast_desc
        BurnMode.ALTERNATE -> R.string.burn_mode_alternate_desc
    }
)

/** 例如「每天」「工作日」「一 三 五」 */
@Composable
fun repeatText(alarm: Alarm): String {
    val days = alarm.repeatDays
    return when {
        days.isEmpty() -> stringResource(R.string.repeat_once)
        days.size == 7 -> stringResource(R.string.repeat_daily)
        days == setOf(1, 2, 3, 4, 5) -> stringResource(R.string.repeat_weekdays)
        days == setOf(6, 7) -> stringResource(R.string.repeat_weekends)
        else -> {
            val names = stringArrayResource(R.array.day_short)
            days.sorted().joinToString(" ") { names[it - 1] }
        }
    }
}
