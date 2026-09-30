package com.dsh.burnalarm.data

import kotlinx.serialization.Serializable
import java.util.Locale

/** 数学题难度。显示名在 ui/Labels.kt 里按当前语言取。 */
@Serializable
enum class MathDifficulty {
    EASY,
    NORMAL,
    HARD
}

/** 焚烧模式。显示名在 ui/Labels.kt 里按当前语言取。 */
@Serializable
enum class BurnMode {
    /** 把 max_tokens 拉满，生成一大段废话然后直接丢弃 */
    WASTE,

    /** 让模型骂你一句，弹在屏幕上并朗读出来 */
    ROAST,

    /** 一次纯烧、一次毒鸡汤，轮着来 */
    ALTERNATE
}

@Serializable
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    /** 重复的星期，ISO 编号：1=周一 … 7=周日。空集合 = 只响一次 */
    val repeatDays: Set<Int> = emptySet(),
    val label: String = "",
    val difficulty: MathDifficulty = MathDifficulty.NORMAL,
    /** 要做对几道题才算过关 */
    val problemCount: Int = 3,
    /** 宽限秒数：响铃后多少秒内没做完就开始烧 */
    val graceSeconds: Int = 60,
    /** 上次响铃时生成的具体题目（用于重新打开时保持题目不变） */
    val lastTriggeredAt: Long = 0L
) {
    /** 例如 "07:30"。固定用 Locale.US，避免某些地区把数字换成别的字形 */
    val timeText: String get() = String.format(Locale.US, "%02d:%02d", hour, minute)
}
