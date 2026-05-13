package com.example.moment.di

import com.example.moment.data.vision.MlKitImageLabelAnalyzer
import com.example.moment.domain.repository.ImageLabelAnalyzer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VisionModule {
    @Binds
    @Singleton
    abstract fun bindImageLabelAnalyzer(impl: MlKitImageLabelAnalyzer): ImageLabelAnalyzer
}
