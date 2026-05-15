package com.example.moment.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.model.AppThemeMode
import com.example.moment.domain.model.UserAppPreferences
import com.example.moment.domain.model.toNasWebdavConfig
import com.example.moment.domain.repository.NasBackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val nasBackupRepository: NasBackupRepository
) : ViewModel() {

    val preferences = userPreferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserAppPreferences()
    )

    private val _aiBaseUrl = MutableStateFlow("")
    private val _aiApiKey = MutableStateFlow("")
    private val _aiModel = MutableStateFlow("")
    val aiBaseUrl: StateFlow<String> = _aiBaseUrl.asStateFlow()
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _nasBaseUrl = MutableStateFlow("")
    private val _nasUsername = MutableStateFlow("")
    private val _nasPassword = MutableStateFlow("")
    private val _nasTrustSelfSigned = MutableStateFlow(false)
    val nasBaseUrl: StateFlow<String> = _nasBaseUrl.asStateFlow()
    val nasUsername: StateFlow<String> = _nasUsername.asStateFlow()
    val nasPassword: StateFlow<String> = _nasPassword.asStateFlow()
    val nasTrustSelfSigned: StateFlow<Boolean> = _nasTrustSelfSigned.asStateFlow()

    private val _nasBusy = MutableStateFlow(false)
    val nasBusy: StateFlow<Boolean> = _nasBusy.asStateFlow()

    private val _nasStatusMessage = MutableStateFlow<String?>(null)
    val nasStatusMessage: StateFlow<String?> = _nasStatusMessage.asStateFlow()

    private val _nasBackupRunIds = MutableStateFlow<List<String>>(emptyList())
    val nasBackupRunIds: StateFlow<List<String>> = _nasBackupRunIds.asStateFlow()

    private val _selectedNasRunId = MutableStateFlow<String?>(null)
    val selectedNasRunId: StateFlow<String?> = _selectedNasRunId.asStateFlow()

    fun reloadDraftFieldsFromStore() {
        viewModelScope.launch {
            val p = userPreferencesRepository.preferences.first()
            _aiBaseUrl.value = p.aiBaseUrl
            _aiApiKey.value = p.aiApiKey
            _aiModel.value = p.aiModel
            _nasBaseUrl.value = p.nasWebdavBaseUrl
            _nasUsername.value = p.nasWebdavUsername
            _nasPassword.value = p.nasWebdavPassword
            _nasTrustSelfSigned.value = p.nasWebdavTrustSelfSignedCertificates
        }
    }

    fun selectTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setAiBaseUrl(value: String) {
        _aiBaseUrl.value = value
    }

    fun setAiApiKey(value: String) {
        _aiApiKey.value = value
    }

    fun setAiModel(value: String) {
        _aiModel.value = value
    }

    fun saveAiSettings() {
        viewModelScope.launch {
            userPreferencesRepository.setAiSettings(
                baseUrl = _aiBaseUrl.value,
                apiKey = _aiApiKey.value,
                model = _aiModel.value
            )
        }
    }

    fun setNasBaseUrl(value: String) {
        _nasBaseUrl.value = value
    }

    fun setNasUsername(value: String) {
        _nasUsername.value = value
    }

    fun setNasPassword(value: String) {
        _nasPassword.value = value
    }

    fun setNasTrustSelfSigned(value: Boolean) {
        _nasTrustSelfSigned.value = value
    }

    fun clearNasStatusMessage() {
        _nasStatusMessage.value = null
    }

    fun saveNasWebdavSettings() {
        viewModelScope.launch {
            userPreferencesRepository.setNasWebdavSettings(
                baseUrl = _nasBaseUrl.value,
                username = _nasUsername.value,
                password = _nasPassword.value,
                trustSelfSignedCertificates = _nasTrustSelfSigned.value
            )
            _nasStatusMessage.value = "NAS 连接信息已保存"
        }
    }

    fun testNasWebdavConnection() {
        viewModelScope.launch {
            _nasBusy.value = true
            _nasStatusMessage.value = null
            val config = currentNasConfigFromForm()
            val r = nasBackupRepository.testWebDavConnection(config)
            _nasBusy.value = false
            _nasStatusMessage.value = r.fold(
                onSuccess = { "连接成功" },
                onFailure = { e -> "连接失败：${e.message ?: e.javaClass.simpleName}" }
            )
        }
    }

    fun backupDiariesToNas() {
        viewModelScope.launch {
            _nasBusy.value = true
            _nasStatusMessage.value = null
            val config = currentNasConfigFromForm()
            val r = nasBackupRepository.backupAllSavedDiaries(config)
            _nasBusy.value = false
            _nasStatusMessage.value = r.fold(
                onSuccess = { s ->
                    "备份完成：${s.diaryCount} 篇日记，图片上传 ${s.imagesUploaded}，跳过 ${s.imagesSkipped}。目录：${s.runFolder}"
                },
                onFailure = { e -> "备份失败：${e.message ?: e.javaClass.simpleName}" }
            )
        }
    }

    fun refreshNasBackupRuns() {
        viewModelScope.launch {
            _nasBusy.value = true
            _nasStatusMessage.value = null
            val config = currentNasConfigFromForm()
            val runs = nasBackupRepository.listBackupRuns(config).getOrElse { e ->
                _nasBusy.value = false
                _nasStatusMessage.value = "读取备份列表失败：${e.message ?: e.javaClass.simpleName}"
                return@launch
            }
            _nasBackupRunIds.value = runs
            if (_selectedNasRunId.value !in runs) {
                _selectedNasRunId.value = runs.firstOrNull()
            }
            _nasBusy.value = false
            _nasStatusMessage.value = when {
                runs.isEmpty() -> "未找到备份目录（MomentBackup/runs/）"
                else -> "找到 ${runs.size} 个备份，请选择一个后点「同步到本机」"
            }
        }
    }

    fun selectNasBackupRun(runId: String) {
        _selectedNasRunId.value = runId
    }

    fun restoreNasSelectedBackup() {
        viewModelScope.launch {
            val runId = _selectedNasRunId.value
            if (runId == null) {
                _nasStatusMessage.value = "请先「刷新备份列表」并选择一个备份"
                return@launch
            }
            _nasBusy.value = true
            _nasStatusMessage.value = null
            val r = nasBackupRepository.restoreBackupRun(currentNasConfigFromForm(), runId)
            _nasBusy.value = false
            _nasStatusMessage.value = r.fold(
                onSuccess = { s ->
                    "同步完成：恢复 ${s.diariesRestored} 篇手帐，跳过 ${s.diariesSkipped} 个目录，图片 ${s.imagesRestored} 张（${s.runId}）"
                },
                onFailure = { e -> "同步失败：${e.message ?: e.javaClass.simpleName}" }
            )
        }
    }

    fun restoreNasLatestBackup() {
        viewModelScope.launch {
            _nasBusy.value = true
            _nasStatusMessage.value = null
            val config = currentNasConfigFromForm()
            val runs = nasBackupRepository.listBackupRuns(config).getOrElse { e ->
                _nasBusy.value = false
                _nasStatusMessage.value = "读取备份列表失败：${e.message ?: e.javaClass.simpleName}"
                return@launch
            }
            _nasBackupRunIds.value = runs
            val latest = runs.firstOrNull()
            if (latest == null) {
                _nasBusy.value = false
                _nasStatusMessage.value = "未找到可同步的备份"
                return@launch
            }
            _selectedNasRunId.value = latest
            val r = nasBackupRepository.restoreBackupRun(config, latest)
            _nasBusy.value = false
            _nasStatusMessage.value = r.fold(
                onSuccess = { s ->
                    "已同步最新备份：恢复 ${s.diariesRestored} 篇，跳过 ${s.diariesSkipped}，图片 ${s.imagesRestored} 张"
                },
                onFailure = { e -> "同步失败：${e.message ?: e.javaClass.simpleName}" }
            )
        }
    }

    private fun currentNasConfigFromForm() =
        UserAppPreferences(
            nasWebdavBaseUrl = _nasBaseUrl.value,
            nasWebdavUsername = _nasUsername.value,
            nasWebdavPassword = _nasPassword.value,
            nasWebdavTrustSelfSignedCertificates = _nasTrustSelfSigned.value
        ).toNasWebdavConfig()
}
