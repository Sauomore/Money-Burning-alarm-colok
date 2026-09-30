package com.dsh.burnalarm.burn

import com.dsh.burnalarm.math.MathProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 响铃 / 焚烧的全局状态。
 *
 * 放在单例里，是为了让「前台服务」和「全屏响铃页」解耦：
 * 服务只负责推进状态，Activity 只负责渲染状态，谁先死都不影响对方。
 */
object BurnState {

    enum class Phase {
        /** 没在响 */
        IDLE,
        /** 已响铃，正在宽限倒计时，还没开始烧 */
        RINGING,
        /** 宽限期过了，正在烧 */
        BURNING,
        /** 用户答对，收工 */
        FINISHED
    }

    data class Status(
        val phase: Phase = Phase.IDLE,
        val alarmId: Long = -1L,
        val alarmLabel: String = "",
        val problems: List<MathProblem> = emptyList(),
        val graceTotalSeconds: Int = 60,
        val graceRemainingSeconds: Int = 60,
        val elapsedSeconds: Long = 0L,
        /** 已发起的 API 调用次数 */
        val calls: Int = 0,
        val promptTokens: Long = 0L,
        val completionTokens: Long = 0L,
        val estimatedCost: Double = 0.0,
        val lastRoast: String = "",
        val lastError: String? = null,
        val apiConfigured: Boolean = true,
        val stopReason: String? = null
    ) {
        val totalTokens: Long get() = promptTokens + completionTokens
        val isBurning: Boolean get() = phase == Phase.BURNING
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    val current: Status get() = _status.value

    fun update(block: (Status) -> Status) {
        _status.update(block)
    }

    fun reset() {
        _status.value = Status()
    }
}
