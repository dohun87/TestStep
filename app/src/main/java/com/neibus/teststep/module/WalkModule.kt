package com.neibus.teststep.module

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import javax.inject.Inject

class WalkModule @Inject constructor(private val mContext: Context): SensorEventListener {

    private val mSensorManager by lazy {
        mContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val mStepDetectCount by lazy {
        mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    private var currentWalkCount = 0

    private lateinit var mListener: WalkModuleListener

    fun setSamsungHealthData(samsungHealthCount: Int) {
        currentWalkCount = samsungHealthCount
    }

    fun initSensor(mListener : WalkModuleListener) {
        this.mListener = mListener
        mSensorManager.registerListener(this,mStepDetectCount, SensorManager.SENSOR_DELAY_FASTEST)
    }

    fun removeSensor() {
        mSensorManager.unregisterListener(this)
    }

    fun resetCurrentStepCount() {
        currentWalkCount = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            currentWalkCount++
            mListener.sendStepCount(currentWalkCount)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    interface WalkModuleListener {
        fun sendStepCount(count: Int)
    }

}