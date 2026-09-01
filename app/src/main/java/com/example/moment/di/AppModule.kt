package com.example.moment.di

import android.content.Context
import androidx.room.Room
import com.example.moment.data.avatar.AvatarCropProcessor
import com.example.moment.data.avatar.UserAvatarStore
import com.example.moment.data.nearby.PeerAvatarStore
import com.example.moment.data.local.DiaryDao
import com.example.moment.data.local.FragmentDao
import com.example.moment.data.local.MIGRATION_1_2
import com.example.moment.data.local.MIGRATION_2_3
import com.example.moment.data.local.MIGRATION_3_4
import com.example.moment.data.local.MIGRATION_4_5
import com.example.moment.data.local.MIGRATION_5_6
import com.example.moment.data.local.MIGRATION_6_7
import com.example.moment.data.local.MIGRATION_7_8
import com.example.moment.data.local.MIGRATION_8_9
import com.example.moment.data.local.MIGRATION_9_10
import com.example.moment.data.local.MIGRATION_10_11
import com.example.moment.data.local.MomentDatabase
import com.example.moment.data.local.NearbyChatDao
import com.example.moment.data.nearby.NearbyShareImageStore
import com.example.moment.data.repository.DiaryRepositoryImpl
import com.example.moment.data.repository.FragmentRepositoryImpl
import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.generator.RuleBasedDiaryGenerator
import com.example.moment.data.nas.NasArchiveSyncLauncher
import com.example.moment.data.nas.NasMomentAccountRepositoryImpl
import com.example.moment.data.nas.NasBackupRepositoryImpl
import com.example.moment.domain.nas.NasArchiveSyncCoordinator
import com.example.moment.domain.repository.NasArchiveRepository
import com.example.moment.domain.repository.NasBackupRepository
import com.example.moment.domain.repository.NasMomentAccountRepository
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindFragmentRepository(impl: FragmentRepositoryImpl): FragmentRepository

    @Binds
    abstract fun bindDiaryRepository(impl: DiaryRepositoryImpl): DiaryRepository

    @Binds
    abstract fun bindNasBackupRepository(impl: NasBackupRepositoryImpl): NasBackupRepository

    @Binds
    abstract fun bindNasArchiveRepository(impl: NasBackupRepositoryImpl): NasArchiveRepository

    @Binds
    @Singleton
    abstract fun bindNasArchiveSyncCoordinator(impl: NasArchiveSyncLauncher): NasArchiveSyncCoordinator

    @Binds
    abstract fun bindNasMomentAccountRepository(impl: NasMomentAccountRepositoryImpl): NasMomentAccountRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MomentDatabase =
        Room.databaseBuilder(context, MomentDatabase::class.java, "moment.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11
            )
            .build()

    @Provides
    fun provideFragmentDao(database: MomentDatabase): FragmentDao = database.fragmentDao()

    @Provides
    fun provideDiaryDao(database: MomentDatabase): DiaryDao = database.diaryDao()

    @Provides
    fun provideNearbyChatDao(database: MomentDatabase): NearbyChatDao = database.nearbyChatDao()

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    fun provideDiaryGenerator(zoneId: ZoneId): DiaryGenerator = RuleBasedDiaryGenerator(zoneId)

    @Provides
    @Singleton
    fun provideUserAvatarStore(@ApplicationContext context: Context): UserAvatarStore =
        UserAvatarStore(context.filesDir)

    @Provides
    @Singleton
    fun providePeerAvatarStore(@ApplicationContext context: Context): PeerAvatarStore =
        PeerAvatarStore(context.filesDir)

    @Provides
    @Singleton
    fun provideNearbyShareImageStore(@ApplicationContext context: Context): NearbyShareImageStore =
        NearbyShareImageStore(context.filesDir)

    @Provides
    @Singleton
    fun provideAvatarCropProcessor(@ApplicationContext context: Context): AvatarCropProcessor =
        AvatarCropProcessor(context.filesDir)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
}
