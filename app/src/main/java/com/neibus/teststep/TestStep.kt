package com.neibus.teststep

import android.app.Application
import com.neibus.teststep.util.DebugTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class TestStep: Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(DebugTree())
    }
}