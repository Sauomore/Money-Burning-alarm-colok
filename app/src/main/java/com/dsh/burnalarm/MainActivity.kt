package com.dsh.burnalarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dsh.burnalarm.i18n.AppLocale.localized
import com.dsh.burnalarm.ui.AppNav
import com.dsh.burnalarm.ui.BurnAlarmTheme

/**
 * 继承 AppCompatActivity 是为了 per-app language（见 i18n/AppLocale.kt）。
 * 界面本身仍然是纯 Compose。
 */
class MainActivity : AppCompatActivity() {

    private val requestNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 用户选什么都继续 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 通知渠道的名称/描述是存在系统里的，换语言后要重新写一遍才会跟着变
        (application as BurnAlarmApp).createChannels(localized())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            BurnAlarmTheme {
                AppNav()
            }
        }
    }
}
