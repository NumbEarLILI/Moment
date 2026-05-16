package com.example.moment.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.model.AppThemeMode
import com.example.moment.domain.model.NasWebdavConfig
import com.example.moment.domain.model.UserAppPreferences
import com.example.moment.domain.model.toNasWebdavConfig
import com.example.moment.domain.repository.NasArchiveRepository
import com.example.moment.domain.repository.NasBackupRepository
import com.example.moment.domain.repository.NasMomentAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val nasBackupRepository: NasBackupRepository,
    private val nasArchiveRepository: NasArchiveRepository,
    private val nasMomentAccountRepository: NasMomentAccountRepository
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

    private val _saveSuccessMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saveSuccessMessage = _saveSuccessMessage.asSharedFlow()

    private val _nasMomentAccountUsernameDraft = MutableStateFlow("")
    private val _nasMomentAccountPasswordDraft = MutableStateFlow("")
    val nasMomentAccountUsernameDraft: StateFlow<String> = _nasMomentAccountUsernameDraft.asStateFlow()
    val nasMomentAccountPasswordDraft: StateFlow<String> = _nasMomentAccountPasswordDraft.asStateFlow()

    fun reloadDraftFieldsFromStore() {
        viewModelScope.launch {
            val p = userPreferencesRepository.preferences.first()
            val bothAiBlank = p.aiBaseUrl.isBlank() && p.aiModel.isBlank()
            _aiBaseUrl.value = if (bothAiBlank) UserAppPreferences.DEFAULT_AI_BASE_URL else p.aiBaseUrl
            _aiApiKey.value = p.aiApiKey
            _aiModel.value = if (bothAiBlank) UserAppPreferences.DEFAULT_AI_MODEL else p.aiModel
            _nasBaseUrl.value = p.nasWebdavBaseUrl
            _nasUsername.value = p.nasWebdavUsername
            _nasPassword.value = p.nasWebdavPassword
            _nasTrustSelfSigned.value = p.nasWebdavTrustSelfSignedCertificates
            _nasMomentAccountUsernameDraft.value = ""
            _nasMomentAccountPasswordDraft.value = ""
        }
    }

    fun setNasMomentAccountUsernameDraft(value: String) {
        _nasMomentAccountUsernameDraft.value = value
    }

    fun setNasMomentAccountPasswordDraft(value: String) {
        _nasMomentAccountPasswordDraft.value = value
    }

    fun registerNasMomentAccount() {
        viewModelScope.launch {
            _nasBusy.value = true
            _nasStatusMessage.value = null
            val r = nasMomentAccountRepository.registerMomentAccount(
                currentNasConfigFromForm(),
                _nasMomentAccountUsernameDraft.value,
                _nasMomentAccountPasswordDraft.value
            )
            _nasBusy.value = false
            if (r.isSuccess) {
                val name = userPreferencesRepository.preferences.first().nasMomentAccountUsername
                _nasMomentAccountPasswordDraft.value = ""
                _nasStatusMessage.value = "注册成功，已登录为「$name」。备份与存档将位于该账号目录下。"
            } else {
                val e = r.exceptionOrNull()
                _nasStatusMessage.value = "注册失败：${e?.message ?: e?.javaClass?.simpleName ?: "错误"}"
            }
        }
    }

    fun loginNasMomentAccount() {
        viewModelScope.launch {
            _nasBusy.value = true
            _nasStatusMessage.value = null
            val r = nasMomentAccountRepository.loginMomentAccount(
                currentNasConfigFromForm(),
                _nasMomentAccountUsernameDraft.value,
                _nasMomentAccountPasswordDraft.value
            )
            _nasBusy.value = false
            if (r.isSuccess) {
                val name = userPreferencesRepository.preferences.first().nasMomentAccountUsername
                _nasMomentAccountPasswordDraft.value = ""
                _nasStatusMessage.value = "已登录「$name」。"
            } else {
                val e = r.exceptionOrNull()
                _nasStatusMessage.value = "登录失败：${e?.message ?: e?.javaClass?.simpleName ?: "错误"}"
            }
        }
    }

    fun logoutNasMomentAccount() {
        viewModelScope.launch {
            userPreferencesRepository.clearNasMomentAccount()
            _nasMomentAccountUsernameDraft.value = ""
            _nasMomentAccountPasswordDraft.value = ""
            _nasStatusMessage.value = "已退出 Moment 账号。未登录时使用根目录下的 MomentBackup / MomentArchive（与旧版本路径一致）。"
        }
    }

    fun selectTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setCustomBackgroundImageUri(uri: String) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomBackgroundImageUri(uri)
            _saveSuccessMessage.emit("背景图已更新")
        }
    }

    fun clearCustomBackgroundImage() {
        viewModelScope.launch {
            userPreferencesRepository.setCustomBackgroundImageUri("")
            _saveSuccessMessage.emit("已恢复默认背景")
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
            _saveSuccessMessage.emit("大模型配置已保存")
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
            _saveSuccessMessage.emit("NAS 配置已保存")
        }
    }

    fun setNasArchiveSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNasArchiveSyncEnabled(enabled)
        }
    }

    fun pushAllDiariesToNasArchive() {
        viewModelScope.launch {
            _nasBusy.value = true
            _nasStatusMessage.value = null
            val config = currentNasConfigFromForm()
            val r = nasArchiveRepository.pushAllDiariesToArchive(config)
            _nasBusy.value = false
            _nasStatusMessage.value = r.fold(
                onSuccess = { s ->
                    "存档上传完成：${s.diaryCount} 篇，图片上传 ${s.imagesUploaded}，跳过 ${s.imagesSkipped}（MomentArchive/diaries/）"
                },
                onFailure = { e -> "存档上传失败：${e.message ?: e.javaClass.simpleName}" }
            )
        }
    }

    fun pullNasArchiveToLocal() {
        viewModelScope.launch {
            _nasBusy.value = true
            _nasStatusMessage.value = null
            val config = currentNasConfigFromForm()
            val r = nasArchiveRepository.pullArchiveToLocal(config)
            _nasBusy.value = false
            _nasStatusMessage.value = r.fold(
                onSuccess = { s ->
                    "存档合并完成：写入 ${s.diariesApplied} 日，跳过 ${s.diariesSkipped}，图片 ${s.imagesRestored} 张"
                },
                onFailure = { e -> "存档拉取失败：${e.message ?: e.javaClass.simpleName}" }
            )
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

    fun deleteNasSelectedBackup() {
        viewModelScope.launch {
            val runId = _selectedNasRunId.value
            if (runId == null) {
                _nasStatusMessage.value = "请先「刷新备份列表」并选择一个备份"
                return@launch
            }
            _nasBusy.value = true
            _nasStatusMessage.value = null
            val config = currentNasConfigFromForm()
            val r = nasBackupRepository.deleteBackupRun(config, runId)
            if (r.isFailure) {
                _nasBusy.value = false
                val e = r.exceptionOrNull()
                _nasStatusMessage.value =
                    "删除失败：${e?.message ?: e?.javaClass?.simpleName ?: "错误"}"
                return@launch
            }
            val runs = nasBackupRepository.listBackupRuns(config).getOrElse { e ->
                _nasBackupRunIds.value = emptyList()
                _selectedNasRunId.value = null
                _nasBusy.value = false
                _nasStatusMessage.value =
                    "已删除远端备份 $runId，但刷新列表失败：${e.message ?: e.javaClass.simpleName}"
                return@launch
            }
            _nasBackupRunIds.value = runs
            _selectedNasRunId.value = runs.firstOrNull()
            _nasBusy.value = false
            _nasStatusMessage.value = when {
                runs.isEmpty() -> "已删除备份 $runId。远端暂无其他备份。"
                else -> "已删除备份 $runId。还可选择其余 ${runs.size} 个备份。"
            }
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

    private fun currentNasConfigFromForm(): NasWebdavConfig =
        preferences.value.copy(
            nasWebdavBaseUrl = _nasBaseUrl.value,
            nasWebdavUsername = _nasUsername.value,
            nasWebdavPassword = _nasPassword.value,
            nasWebdavTrustSelfSignedCertificates = _nasTrustSelfSigned.value
        ).toNasWebdavConfig()
}
