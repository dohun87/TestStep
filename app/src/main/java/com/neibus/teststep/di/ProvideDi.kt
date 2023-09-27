package com.neibus.teststep.di

import android.content.Context
import com.neibus.teststep.module.HealthConnectModule
import com.neibus.teststep.module.PreferencesModule
import com.neibus.teststep.module.WalkModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProvideDi {

    @Singleton
    @Provides
    fun provideHealthConnectModule(@ApplicationContext context: Context) = HealthConnectModule(context)

    @Singleton
    @Provides
    fun providePreferencesModule(@ApplicationContext context: Context) = PreferencesModule(context)

    @Singleton
    @Provides
    fun provideWalkModule(@ApplicationContext context: Context) = WalkModule(context)
}