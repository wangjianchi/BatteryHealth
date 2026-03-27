package com.batteryhealth.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batteryhealth.app.data.model.ChargingRecord
import com.batteryhealth.app.data.model.ChargingSession
import com.batteryhealth.app.data.model.ChargingSessionState
import com.batteryhealth.app.data.repository.BatteryDatabase
import com.batteryhealth.app.data.repository.BatteryRepository
import com.batteryhealth.app.manager.BatteryManager
import com.batteryhealth.app.manager.BatteryInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordFormState(
    val ratedCapacity: String = "",
    val batteryBefore: String = "",
    val batteryAfter: String = "",
    val chargingMinutes: String = "",
    val chargingWatt: String = "",
    val note: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class HomeUiState(
    val averageCapacity: Int? = null,
    val healthPercent: Int? = null,
    val recordCount: Int = 0,
    val monitoringDays: Long = 0L,
    val latestRatedCapacity: Int? = null
)

data class ChargingMonitorUiState(
    val batteryInfo: BatteryInfo = BatteryInfo(),
    val sessionState: ChargingSessionState = ChargingSessionState.NotCharging,
    val autoRecordEnabled: Boolean = true,
    val lastUpdate: Long = System.currentTimeMillis()
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BatteryRepository
    private val batteryManager = BatteryManager(application)

    val recordsDesc: StateFlow<List<ChargingRecord>>
    val recordsAsc: StateFlow<List<ChargingRecord>>

    private val _formState = MutableStateFlow(RecordFormState())
    val formState: StateFlow<RecordFormState> = _formState.asStateFlow()

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _monitorState = MutableStateFlow(ChargingMonitorUiState())
    val monitorState: StateFlow<ChargingMonitorUiState> = _monitorState.asStateFlow()

    init {
        val db = BatteryDatabase.getDatabase(application)
        repository = BatteryRepository(db.chargingRecordDao())

        recordsDesc = repository.allRecordsDesc.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )
        recordsAsc = repository.allRecordsAsc.stateIn(
            viewModelScope, SharingStarted.Lazily, emptyList()
        )

        viewModelScope.launch {
            recordsAsc.collect { records ->
                updateHomeState(records)
            }
        }
    }

    private fun updateHomeState(records: List<ChargingRecord>) {
        if (records.isEmpty()) {
            // 没有记录时，显示系统读取的容量
            val currentCapacity = batteryManager.getBatteryInfo().ratedCapacity
            _homeState.value = HomeUiState(
                latestRatedCapacity = if (currentCapacity > 0) currentCapacity else null
            )
            return
        }

        val avgCap = repository.calculateWeightedAverageCapacity(records)

        // 获取系统当前读取的容量（优先）
        val currentSystemCapacity = batteryManager.getBatteryInfo().ratedCapacity

        // 从历史记录中获取容量，但过滤掉不合理的值（>10000mAh很可能是错误数据）
        val historicalCapacity = records.map { it.ratedCapacity }
            .filter { it in 1000..10000 }  // 合理范围：1000-10000mAh
            .lastOrNull()

        // 优先使用系统读取，回退到合理的历史记录
        val latestRated = when {
            currentSystemCapacity > 0 -> currentSystemCapacity  // 优先：系统读取
            historicalCapacity != null -> historicalCapacity     // 备用：合理的历史值
            else -> records.lastOrNull()?.ratedCapacity          // 最后：原始值
        }

        val healthPct = if (avgCap != null && latestRated != null) {
            repository.calculateHealthPercent(avgCap, latestRated)
        } else null

        val days = if (records.size >= 2) {
            val firstMs = records.first().timestamp
            val lastMs = records.last().timestamp
            ((lastMs - firstMs) / 86400000L).coerceAtLeast(1L)
        } else 1L

        _homeState.value = HomeUiState(
            averageCapacity = avgCap,
            healthPercent = healthPct,
            recordCount = records.size,
            monitoringDays = days,
            latestRatedCapacity = latestRated
        )
    }

    fun updateForm(
        ratedCapacity: String? = null,
        batteryBefore: String? = null,
        batteryAfter: String? = null,
        chargingMinutes: String? = null,
        chargingWatt: String? = null,
        note: String? = null
    ) {
        _formState.value = _formState.value.copy(
            ratedCapacity = ratedCapacity ?: _formState.value.ratedCapacity,
            batteryBefore = batteryBefore ?: _formState.value.batteryBefore,
            batteryAfter = batteryAfter ?: _formState.value.batteryAfter,
            chargingMinutes = chargingMinutes ?: _formState.value.chargingMinutes,
            chargingWatt = chargingWatt ?: _formState.value.chargingWatt,
            note = note ?: _formState.value.note,
            errorMessage = null
        )
    }

    fun submitRecord() {
        val state = _formState.value

        val rated = state.ratedCapacity.toIntOrNull()
        val before = state.batteryBefore.toIntOrNull()
        val after = state.batteryAfter.toIntOrNull()
        val minutes = state.chargingMinutes.toIntOrNull()
        val watt = state.chargingWatt.toFloatOrNull()

        // 验证
        if (rated == null || rated <= 0) {
            _formState.value = state.copy(errorMessage = "请输入有效的标称容量（mAh）")
            return
        }
        if (before == null || before < 0 || before > 99) {
            _formState.value = state.copy(errorMessage = "充电前电量应在 0–99%")
            return
        }
        if (after == null || after <= before || after > 100) {
            _formState.value = state.copy(errorMessage = "充电后电量必须大于充电前电量，且 ≤ 100%")
            return
        }
        if (minutes == null || minutes <= 0) {
            _formState.value = state.copy(errorMessage = "请输入有效的充电时长（分钟）")
            return
        }
        if (state.chargingWatt.isNotBlank() && watt == null) {
            _formState.value = state.copy(errorMessage = "充电功率格式不正确，请输入数字或留空")
            return
        }

        _formState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val estimatedCap = repository.calculateEstimatedCapacity(
                rated, before, after, minutes, watt
            )
            val healthPct = repository.calculateHealthPercent(estimatedCap, rated)

            val record = ChargingRecord(
                ratedCapacity = rated,
                batteryBefore = before,
                batteryAfter = after,
                chargingMinutes = minutes,
                chargingWatt = watt,
                note = state.note.trim(),
                estimatedCapacity = estimatedCap,
                healthPercent = healthPct
            )
            repository.insertRecord(record)

            _formState.value = RecordFormState(
                successMessage = "记录已保存！估算容量：${estimatedCap} mAh，健康度：${healthPct}%"
            )
        }
    }

    fun deleteRecord(record: ChargingRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
        }
    }

    fun clearSuccessMessage() {
        _formState.value = _formState.value.copy(successMessage = null)
    }

    fun resetForm() {
        _formState.value = RecordFormState(
            ratedCapacity = _formState.value.ratedCapacity // 保留标称容量方便复用
        )
    }

    // ========== 充电监控相关方法 ==========

    /**
     * 更新电池信息
     */
    fun updateBatteryInfo() {
        val batteryInfo = batteryManager.getBatteryInfo()
        _monitorState.value = _monitorState.value.copy(
            batteryInfo = batteryInfo,
            lastUpdate = System.currentTimeMillis()
        )

        // 如果启用了自动记录，处理充电会话
        handleChargingSession(batteryInfo)
    }

    /**
     * 处理充电会话
     */
    private fun handleChargingSession(batteryInfo: BatteryInfo) {
        val currentState = _monitorState.value.sessionState

        when {
            // 开始充电
            batteryInfo.isCharging && currentState !is ChargingSessionState.Charging -> {
                val startPercent = batteryInfo.batteryPercent
                val ratedCapacity = batteryInfo.ratedCapacity

                if (startPercent > 0 && ratedCapacity > 0) {
                    val session = ChargingSession(
                        startPercent = startPercent,
                        ratedCapacity = ratedCapacity,
                        currentPercent = startPercent
                    )
                    _monitorState.value = _monitorState.value.copy(
                        sessionState = ChargingSessionState.Charging(session)
                    )
                }
            }
            // 正在充电，更新会话
            batteryInfo.isCharging && currentState is ChargingSessionState.Charging -> {
                val updatedSession = currentState.session.updateSession(
                    currentPercent = batteryInfo.batteryPercent,
                    currentPower = batteryInfo.chargingPower,
                    voltage = batteryInfo.voltageV,
                    temperature = batteryInfo.temperatureC
                )
                _monitorState.value = _monitorState.value.copy(
                    sessionState = ChargingSessionState.Charging(updatedSession)
                )
            }
            // 充电结束
            !batteryInfo.isCharging && currentState is ChargingSessionState.Charging -> {
                val completedSession = currentState.session.endSession(batteryInfo.batteryPercent)

                // 如果充电量超过5%，自动保存记录
                if (completedSession.chargedPercent >= 5f && _monitorState.value.autoRecordEnabled) {
                    viewModelScope.launch {
                        completedSession.toChargingRecord()?.let { record ->
                            repository.insertRecord(record)
                        }
                    }
                }

                _monitorState.value = _monitorState.value.copy(
                    sessionState = ChargingSessionState.Completed(completedSession)
                )
            }
            // 未充电状态
            !batteryInfo.isCharging && currentState !is ChargingSessionState.Charging -> {
                // 保持当前状态不变，避免清除已完成的会话
            }
        }
    }

    /**
     * 手动开始充电会话
     */
    fun startChargingSession(ratedCapacity: Int) {
        val batteryInfo = batteryManager.getBatteryInfo()
        val startPercent = batteryInfo.batteryPercent

        if (startPercent > 0 && ratedCapacity > 0) {
            val session = ChargingSession(
                startPercent = startPercent,
                ratedCapacity = ratedCapacity,
                currentPercent = startPercent
            )
            _monitorState.value = _monitorState.value.copy(
                sessionState = ChargingSessionState.Charging(session)
            )
        }
    }

    /**
     * 手动结束充电会话
     */
    fun endChargingSession() {
        val currentState = _monitorState.value.sessionState
        if (currentState is ChargingSessionState.Charging) {
            val batteryInfo = batteryManager.getBatteryInfo()
            val completedSession = currentState.session.endSession(batteryInfo.batteryPercent)

            _monitorState.value = _monitorState.value.copy(
                sessionState = ChargingSessionState.Completed(completedSession)
            )
        }
    }

    /**
     * 清除已完成的会话
     */
    fun clearCompletedSession() {
        val batteryInfo = batteryManager.getBatteryInfo()
        if (!batteryInfo.isCharging) {
            _monitorState.value = _monitorState.value.copy(
                sessionState = ChargingSessionState.NotCharging
            )
        }
    }

    /**
     * 切换自动记录开关
     */
    fun toggleAutoRecord(enabled: Boolean) {
        _monitorState.value = _monitorState.value.copy(
            autoRecordEnabled = enabled
        )
    }

    /**
     * 保存当前充电会话为记录
     */
    fun saveChargingSession() {
        val currentState = _monitorState.value.sessionState
        if (currentState is ChargingSessionState.Completed) {
            viewModelScope.launch {
                currentState.session.toChargingRecord()?.let { record ->
                    repository.insertRecord(record)
                    _monitorState.value = _monitorState.value.copy(
                        sessionState = ChargingSessionState.NotCharging
                    )
                }
            }
        }
    }
}
