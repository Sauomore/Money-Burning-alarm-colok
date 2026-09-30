package com.dsh.burnalarm.burn

import android.content.Context
import com.dsh.burnalarm.R
import com.dsh.burnalarm.i18n.AppLocale.localized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class LlmResult(
    val content: String,
    val promptTokens: Int,
    val completionTokens: Int
)

class LlmException(message: String) : Exception(message)

/**
 * 极简 OpenAI 兼容客户端。
 * 只依赖 /v1/chat/completions 这一个端点，所以任何兼容实现都能接。
 *
 * 提示词和报错都走字符串资源，所以这里持有一个「按 App 内语言本地化过」的 Context，
 * 中文界面烧出来的是中文毒鸡汤，英文界面就是英文的。
 */
class LlmClient(context: Context) {

    private val ctx = context.localized()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ---------------------------------------------------------------- 提示词

    /** 「纯烧」：目标是让模型把 max_tokens 全部吐满 —— 输出越多，烧得越狠 */
    fun wasteSystem(): String = ctx.getString(R.string.llm_waste_system)

    fun wasteUser(round: Int): String = ctx.getString(R.string.llm_waste_user, round)

    /** 「毒鸡汤」：一句话嘲讽，会显示在屏幕上并朗读出来 */
    fun roastSystem(): String = ctx.getString(R.string.llm_roast_system)

    fun roastUser(round: Int): String = ctx.getString(R.string.llm_roast_user, round)

    // ---------------------------------------------------------------- 请求

    suspend fun chat(
        settings: com.dsh.burnalarm.data.AppSettings,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double = 1.0
    ): LlmResult = withContext(Dispatchers.IO) {

        val payload = buildJsonObject {
            put("model", settings.model)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("stream", false)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                }
            }
        }.toString()

        val request = Request.Builder()
            .url(settings.chatCompletionsUrl())
            .addHeader("Authorization", "Bearer ${settings.apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw LlmException(
                ctx.getString(
                    R.string.llm_err_network,
                    e.message ?: e.javaClass.simpleName
                )
            )
        }

        response.use { resp ->
            val text = resp.body?.string().orEmpty()

            if (!resp.isSuccessful) {
                val brief = text.take(300).replace("\n", " ")
                throw LlmException("HTTP ${resp.code}: $brief")
            }

            val root = try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw LlmException(ctx.getString(R.string.llm_err_bad_json))
            }

            // 有些服务在 200 里塞 error
            root["error"]?.let { err ->
                val msg = err.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                if (!msg.isNullOrBlank()) throw LlmException(msg)
            }

            val content = root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()

            val usage = root["usage"]?.jsonObject
            val promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0
            val completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0

            // 有些兼容实现不返回 usage，用字符数粗略估算，避免统计为 0
            val fallbackCompletion = if (completionTokens == 0) estimateTokens(content) else 0
            val fallbackPrompt =
                if (promptTokens == 0) estimateTokens(systemPrompt) + estimateTokens(userPrompt) else 0

            LlmResult(
                content = content,
                promptTokens = promptTokens + fallbackPrompt,
                completionTokens = completionTokens + fallbackCompletion
            )
        }
    }

    /** 粗略估算：中文约 1 字 1 token，英文约 4 字符 1 token，取折中 1.5 字符/token */
    private fun estimateTokens(text: String): Int =
        if (text.isEmpty()) 0 else (text.length / 1.5).toInt().coerceAtLeast(1)
}
