package com.dsh.burnalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.burnalarm.alarm.AlarmScheduler
import com.dsh.burnalarm.data.Alarm
import com.dsh.burnalarm.data.AlarmRepository
import com.dsh.burnalarm.data.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AlarmRepository.get(app)

    val alarms: StateFlow<List<Alarm>> = repo.alarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 初始值刻意用 null，而不是 AppSettings()。
     *
     * 如果拿一份默认配置当占位值，设置页会把它当成「已读到的真实配置」填进草稿，
     * 用户一进设置页立刻返回，就会把已经存好的 API Key 覆盖成空。
     * null 表示「还没读出来」，设置页据此决定是先转圈还是渲染表单。
     */
    val settings: StateFlow<AppSettings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun upsert(alarm: Alarm) = viewModelScope.launch {
        repo.upsert(alarm)
        rescheduleAll()
    }

    fun delete(id: Long) = viewModelScope.launch {
        repo.delete(id)
        rescheduleAll()
    }

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        repo.setEnabled(id, enabled)
        rescheduleAll()
    }

    fun saveSettings(value: AppSettings) = viewModelScope.launch {
        repo.saveSettings(value)
    }

    fun nextId(onReady: (Long) -> Unit) = viewModelScope.launch {
        onReady(repo.nextId())
    }

    private suspend fun rescheduleAll() {
        AlarmScheduler.rescheduleAll(getApplication(), repo.currentAlarms())
    }
}
