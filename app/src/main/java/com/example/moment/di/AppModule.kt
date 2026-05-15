package com.example.moment.di

import android.content.Context
import androidx.room.Room
import com.example.moment.data.local.DiaryDao
import com.example.moment.data.local.FragmentDao
import com.example.moment.data.local.MIGRATION_1_2
import com.example.moment.data.local.MIGRATION_2_3
import com.example.moment.data.local.MIGRATION_3_4
import com.example.moment.data.local.MomentDatabase
import com.example.moment.data.preferences.DataStoreUserPreferencesRepository
import com.example.moment.data.remote.openai.OpenAiCompatibleDiarySynthesizer
import com.example.moment.data.repository.DiaryRepositoryImpl
import com.example.moment.data.repository.FragmentRepositoryImpl
import com.example.moment.domain.diary.AiDiarySynthesizer
import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.generator.RuleBasedDiaryGenerator
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import com.example.moment.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindFragmentRepository(impl: FragmentRepositoryImpl): FragmentRepository

    @Binds
    abstract fun bindDiaryRepository(impl: DiaryRepositoryImpl): DiaryRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: DataStoreUserPreferencesRepository
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindAiDiarySynthesizer(impl: OpenAiCompatibleDiarySynthesizer): AiDiarySynthesizer
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MomentDatabase =
        Room.databaseBuilder(context, MomentDatabase::class.java, "moment.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun provideFragmentDao(database: MomentDatabase): FragmentDao = database.fragmentDao()

    @Provides
    fun provideDiaryDao(database: MomentDatabase): DiaryDao = database.diaryDao()

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    fun provideDiaryGenerator(zoneId: ZoneId): DiaryGenerator = RuleBasedDiaryGenerator(zoneId)
}
