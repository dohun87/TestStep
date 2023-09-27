package com.neibus.teststep

import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import com.neibus.teststep.define.IntentDefine
import com.neibus.teststep.module.PreferencesModule
import com.neibus.teststep.module.WalkModule
import com.neibus.teststep.util.NotificationUtils
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WalkService(): LifecycleService(), WalkModule.WalkModuleListener {

    @Inject lateinit var mPrefModule: PreferencesModule
    @Inject lateinit var mWalkModule: WalkModule

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private var notiId = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if(intent?.action == IntentDefine.WALK_START.action) {
            val initWalkCount = mPrefModule.loadCurrentWalkPreferences()
            Timber.d("initWalkCount: $initWalkCount")
            mWalkModule.initSensor(this)
            Timber.d("enable Notification!!")
            NotificationUtils.createNotification(this,initWalkCount) { notiId, notification ->
                this.notiId = notiId
                startForeground(notiId,notification.build())
            }
        } else if(intent?.action == IntentDefine.WALK_END.action) {
            mWalkModule.removeSensor()
            @Suppress("DEPRECATION")
            stopForeground(true)
            notiId = 0
            stopSelf()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun sendStepCount(count: Int) {
        mPrefModule.saveCurrentWalkPreferences(count)
        val service = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        service.putExtra(IntentDefine.SEND_WALK_COUNT.action,count)
        startActivity(service)
        if(notiId!=0) {
            NotificationUtils.createNotification(this,count) { notiId, notification ->
                this.notiId = notiId
                val mNotificationManager =
                    getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                mNotificationManager.notify(notiId, notification.build())
            }
        }
    }
}