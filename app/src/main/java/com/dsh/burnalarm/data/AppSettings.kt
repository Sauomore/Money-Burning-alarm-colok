package com.dsh.burnalarm.data

import kotlinx.serialization.Serializable

/**
 * 全局配置。API 相关与焚烧策略都在这里。
 */
@Serializable
data class AppSettings(
    /** OpenAI 兼容接口的基地址，例如 https://api.deepseek.com 或 https://api.openai.com */
    val baseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-chat",

    /** 计价用：每 100 万输入 token 的价格（人民币元） */
    val inputPricePerMillion: Double = 2.0,
    /** 计价用：每 100 万输出 token 的价格（人民币元） */
    val outputPricePerMillion: Double = 8.0,

    /** 每隔几秒烧一次 */
    val burnIntervalSeconds: Int = 3,
    /** 单次请求最大输出 token —— 这就是「烧」的主要来源 */
    val maxOutputTokens: Int = 4096,
    val burnMode: BurnMode = BurnMode.ALTERNATE,
    /** 毒鸡汤是否朗读出来 */
    val speakRoast: Boolean = true,

    /**
     * 跑飞保护：连续焚烧超过这么多分钟就强制停下（0 = 不限制）。
     *
     * 这一项防的是「代码缺陷导致的无限循环」，不是防你主动选择的无上限。
     * 如果你确实想烧到天荒地老，把它设成 0 即可。
     */
    val runawayGuardMinutes: Int = 120,

    /** 是否已经看过「这会花你真钱」的确认弹窗 */
    val costWarningAccepted: Boolean = false
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    /** 拼出 chat/completions 的完整地址，容忍用户填不填 /v1 */
    fun chatCompletionsUrl(): String {
        val base = baseUrl.trim().trimEnd('/')
        val withV1 = if (base.endsWith("/v1")) base else "$base/v1"
        return "$withV1/chat/completions"
    }
}
