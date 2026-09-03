package com.example.moment.domain.repository

import com.example.moment.domain.model.NasWebdavConfig
import com.example.moment.domain.naschat.MomentAccountRef

/**
 * 在 WebDAV 根目录下维护 `MomentApp/account_registry.json`，用于多用户隔离存档/备份路径。
 */
interface NasMomentAccountRepository {
    suspend fun registerMomentAccount(config: NasWebdavConfig, username: String, password: String): Result<Unit>

    suspend fun loginMomentAccount(config: NasWebdavConfig, username: String, password: String): Result<Unit>

    suspend fun listMomentAccounts(config: NasWebdavConfig): Result<List<MomentAccountRef>>
}
