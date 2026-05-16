package com.example.moment.di

import com.example.moment.data.llm.AiDiaryDraftGeneratorImpl
import com.example.moment.data.llm.LlmImageTagSuggesterImpl
import com.example.moment.data.preferences.UserPreferencesAccessorImpl
import com.example.moment.domain.llm.AiDiaryDraftGenerator
import com.example.moment.domain.llm.LlmImageTagSuggester
import com.example.moment.domain.preferences.UserPreferencesAccessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {
    @Binds
    @Singleton
    abstract fun bindAiDiaryDraftGenerator(impl: AiDiaryDraftGeneratorImpl): AiDiaryDraftGenerator

    @Binds
    @Singleton
    abstract fun bindLlmImageTagSuggester(impl: LlmImageTagSuggesterImpl): LlmImageTagSuggester

    @Binds
    @Singleton
    abstract fun bindUserPreferencesAccessor(impl: UserPreferencesAccessorImpl): UserPreferencesAccessor
}
