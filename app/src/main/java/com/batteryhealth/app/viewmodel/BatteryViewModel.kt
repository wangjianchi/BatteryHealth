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
import com.batteryhealth.app.manager.HealthEstimationResult
import com.batteryhealth.app.manager.HealthRecordInput
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
    val latestRatedCapacity: Int? = null,
    val systemHealthPercent: Int = -1,
    val healthEstimations: HealthEstimationResult? = null
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
        // 获取系统健康度（从 sysfs 读取，不依赖充电记录）
        val batteryInfo = batteryManager.getBatteryInfo()
        val systemHealth = batteryInfo.systemHealthPercent

        // 准备历史记录输入（用于功率积分、百分比法、历史加权）
        val healthRecordInputs = records.map { r ->
            HealthRecordInput(
                ratedCapacity = r.ratedCapacity,
                chargedPercent = (r.batteryAfter - r.batteryBefore).toFloat(),
                chargedMinutes = r.chargingMinutes,
                chargingWatt = r.chargingWatt
            )
        }

        // 计算所有5种健康度估算方案
        val estimations = batteryManager.calculateAllHealthEstimations(healthRecordInputs)

        if (records.isEmpty()) {
            _homeState.value = HomeUiState(
                latestRatedCapacity = if (batteryInfo.ratedCapacity > 0) batteryInfo.ratedCapacity else null,
                systemHealthPercent = systemHealth,
                healthEstimations = estimations
            )
            return
        }

        val avgCap = repository.calculateWeightedAverageCapacity(records)
        val currentSystemCapacity = batteryInfo.ratedCapacity

        val historicalCapacity = records.map { it.ratedCapacity }
            .filter { it in 1000..10000 }
            .lastOrNull()

        val latestRated = when {
            currentSystemCapacity > 0 -> currentSystemCapacity
            historicalCapacity != null -> historicalCapacity
            else -> records.lastOrNull()?.ratedCapacity
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
            latestRatedCapacity = latestRated,
            systemHealthPercent = systemHealth,
            healthEstimations = estimations
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
        val isChargerConnected = batteryInfo.plugged != 0  // plugged != 0 表示充电器连接中

        when {
            // 充电器已断开，结束充电会话
            !isChargerConnected && currentState is ChargingSessionState.Charging -> {
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
            // 充电器已连接（plugged != 0），开始或继续充电会话
            isChargerConnected -> {
                when (currentState) {
                    // 充电器连接且未满电，开启新会话
                    is ChargingSessionState.NotCharging,
                    is ChargingSessionState.Completed -> {
                        if (batteryInfo.batteryPercent < 100f && batteryInfo.batteryPercent > 0f && batteryInfo.ratedCapacity > 0) {
                            val session = ChargingSession(
                                startPercent = batteryInfo.batteryPercent,
                                ratedCapacity = batteryInfo.ratedCapacity,
                                currentPercent = batteryInfo.batteryPercent
                            )
                            _monitorState.value = _monitorState.value.copy(
                                sessionState = ChargingSessionState.Charging(session)
                            )
                        }
                    }
                    // 继续更新充电会话
                    is ChargingSessionState.Charging -> {
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
                }
            }
            // 充电器未连接且无充电会话，保持空闲状态
            else -> {
                // 保持 NotCharging 状态
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
