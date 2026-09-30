package com.dsh.burnalarm.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 应用内语言切换。
 *
 * 底层用 AppCompatDelegate.setApplicationLocales：
 *  - Android 13+ 交给系统的 per-app language 机制，系统设置里也会出现入口
 *  - Android 13 以下由 AppCompat 自己存偏好并重建 Activity
 * 两种情况都不需要我们手工管理持久化和 recreate()。
 */
object AppLocale {

    /** 空串 = 跟随系统 */
    const val SYSTEM = ""
    const val ZH = "zh"
    const val EN = "en"

    val OPTIONS = listOf(SYSTEM, ZH, EN)

    /** 当前选定的语言标签；空串表示跟随系统 */
    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return SYSTEM
        val locale = locales[0] ?: return SYSTEM
        return locale.language ?: SYSTEM
    }

    fun apply(tag: String) {
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** 把语言标签映射到 strings.xml 里的显示名 */
    fun labelRes(tag: String): Int = when (tag) {
        ZH -> com.dsh.burnalarm.R.string.language_zh
        EN -> com.dsh.burnalarm.R.string.language_en
        else -> com.dsh.burnalarm.R.string.language_system
    }

    /**
     * 给 Service / BroadcastReceiver 用。
     *
     * setApplicationLocales 在 Android 13 以下只接管 Activity，Service 和 Receiver
     * 拿到的仍是系统语言的 Context —— 通知里的文案就会跟 App 内选的语言对不上。
     * 这里手动按当前偏好包一层 Context。
     */
    fun Context.localized(): Context {
        val tag = currentTag()
        if (tag.isBlank()) return this
        val config = Configuration(resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return createConfigurationContext(config)
    }
}
