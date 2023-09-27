package com.neibus.teststep.module

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.Exception
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import javax.inject.Inject

class HealthConnectModule @Inject constructor(private val context: Context) {

    companion object {
        private const val SAMSUNGHEALTH_PACKAGE = "com.sec.android.app.shealth"
    }

    /**
     * 구글 헬스 커넥트에 대한 현재 상태에 대한 Enum Class
     *
     * @author Alt
     * @since 2023-09-08
     */
    enum class InitResultType(val define: Int) {
        NOT_USABLE(-1),
        PERMISSION_OK(0),
        NOT_PERMISSION(1),
        NOT_INSTALL(2),
    }

    /**
     * 삼성헬스 상태에 대한 Enum Class
     */
    enum class SamsungHealthConnectStatus(val define: Int) {
        CONNECT_OK(11),
        NOT_CONNECT(12),
    }

    private val PERMISSIONS =
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class)
        )

    private val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()


    private lateinit var mActivity: AppCompatActivity

    private lateinit var requestPermissions: ActivityResultLauncher<Set<String>>

    private lateinit var mInitCallback: (Int) -> Unit

    private lateinit var healthConnectClient: HealthConnectClient

    private lateinit var mListener: HealthConnectResultListener

    fun setListener(mListener: HealthConnectResultListener) {
        this.mListener = mListener
    }

    fun availHealthConnectStatus(mActivity: AppCompatActivity, mCallback: (Int) -> Unit) {
        mInitCallback = mCallback
        this.mActivity = mActivity
        mActivity.lifecycleScope.launch {
            val availabilityStatus = HealthConnectClient.getSdkStatus(context)
            if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
                mCallback(InitResultType.NOT_USABLE.define)
                Timber.d("SDK_UNAVAILABLE")
                return@launch // early return as there is no viable integration
            }
            if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                Timber.d("SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED")
                // Optionally redirect to package installer to find a provider, for example:
                mCallback(InitResultType.NOT_INSTALL.define)
                return@launch
            }
            Timber.d("Init Health Connect")
            healthConnectClient = HealthConnectClient.getOrCreate(context)
            initPermission()
            checkPermissionsAndRun()
        }
    }

    fun isHealConnectInstalled(): Boolean {
        val availabilityStatus = HealthConnectClient.getSdkStatus(context)
        return availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
    }

    private fun initPermission() {
        requestPermissions = mActivity.registerForActivityResult(requestPermissionActivityContract) { granted ->
            if (granted.containsAll(PERMISSIONS)) {
                Timber.d("Permission is Grant!!")
                mInitCallback(InitResultType.PERMISSION_OK.define)
            } else {
                Timber.d("Permission is Not!!")
                mInitCallback(InitResultType.NOT_PERMISSION.define)
            }
        }
    }

    suspend fun checkPermissionsAndRun() {
        if(::healthConnectClient.isInitialized) {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (granted.containsAll(PERMISSIONS)) {
                // Permissions already granted; proceed with inserting or reading data
                Timber.d("Permission is Grant!!")
                mInitCallback(InitResultType.PERMISSION_OK.define)
            getDistanceData()
            readStepsByTimeRange()
            aggregateWalk()
            aggregateStepsIntoMinutes()
            aggregateStepsIntoMonths()
            } else {
                mInitCallback(InitResultType.NOT_PERMISSION.define)
            }
        }
    }

    fun executePermissions() {
        requestPermissions.launch(PERMISSIONS)
    }

    /**
     * 거리에 대한 간단한 읽기 메소드
     * 해당 기능을 이용하였을 시 삼성헬스의 데이터에 접근이 되지 않는다.
     * 내부의 앱 데이터로 판단 된다.
     *
     * @author Alt
     * @since 2023-09-11
     */
    suspend fun getDistanceData() {
        try {
            val startTime = LocalDate.now().atStartOfDay()
            val endTime = LocalDate.now().plusDays(1).atStartOfDay()
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = setOf(DataOrigin(SAMSUNGHEALTH_PACKAGE))
                )
            )
            response[DistanceRecord.DISTANCE_TOTAL]?.let { walkTotal ->
                Timber.d("distance - response : $walkTotal")
                mListener.sendSamsungHealthWalkData(walkTotal.inMeters.toLong())
                //mListener.checkedSamsungHealthStatus(SamsungHealthConnectStatus.CONNECT_OK.define)
            } ?: kotlin.run {
                Timber.d("currentData is Null!!")
                //mListener.checkedSamsungHealthStatus(SamsungHealthConnectStatus.NOT_CONNECT.define)
            }
        }catch (e: Exception) {
            Timber.e("error Message : ${e.printStackTrace()}")
        }
    }

    /**
     * 현재 저장되어 있는 삼성 헬스 데이터를 가져오기 위한 메소드(이거는 구분되어 있지 않은 내역)
     *
     * @author Alt
     * @since 2023-09-26
     */
    suspend fun readStepsByTimeRange() {
        try {
            val startTime = LocalDate.now().atStartOfDay()
            val endTime = LocalDate.now().plusDays(1).atStartOfDay()
            Timber.d("startTime : $startTime")
            Timber.d("endTime: $endTime")
            val response =
                healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                        dataOriginFilter = setOf(DataOrigin("com.sec.android.app.shealth"))
                    )
                )
            for (stepRecord in response.records) {
                // Process each step record
                Timber.d("readStepsByTimeRange-record : ${stepRecord.count}")
            }
        } catch (e: Exception) {
            // Run error handling here.
            Timber.d("not Data!!")
        }
    }

    /**
     * 1달치의 걸음수를 가져오는 데이터를 가져오는 로직
     *
     * @author Alt
     * @since 2023-09-26
     */
    suspend fun aggregateStepsIntoMonths() {
        try {
            val startTime = LocalDate.now().atStartOfDay()
            val endTime = LocalDate.now().plusDays(1).atStartOfDay()
            Timber.d("startTime : $startTime")
            Timber.d("endTime: $endTime")
            val response =
                healthConnectClient.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                        timeRangeSlicer = Period.ofDays(1)
                    )
                )
            Timber.d("aggregateStepsIntoMonths-size: ${response.size}")
            for (monthlyResult in response) {
                // The result may be null if no data is available in the time range
                val totalSteps = monthlyResult.result[StepsRecord.COUNT_TOTAL]
            }
        } catch (e: Exception) {
            // Run error handling here
        }
    }

    /**
     * Google Health Connect서 1분단위로 가져오기 위한 메소드
     * 실제 사용시 제대로 가져오지 않는것으로 판단됨
     *
     * @author Alt
     * @since 2023-09-26
     */
    suspend fun aggregateStepsIntoMinutes() {
        try {
            val startTime = LocalDate.now().atStartOfDay()
            val endTime = LocalDate.now().plusDays(1).atStartOfDay()
            Timber.d("startTime : $startTime")
            Timber.d("endTime: $endTime")
            val response =
                healthConnectClient.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                        timeRangeSlicer = Duration.ofMinutes(1L)
                    )
                )
            Timber.d("aggregateStepsIntoMinutes-responseSize : ${response.size}")
            var totalSteps = 0L
            for (durationResult in response) {
                // The result may be null if no data is available in the time range
                totalSteps += durationResult.result[StepsRecord.COUNT_TOTAL]!!
                //Timber.d("totalSteps: $totalSteps")
            }
            Timber.d("aggregateStepsIntoMinutes-totalWalk : $totalSteps")
        } catch (e: Exception) {
            // Run error handling here
        }
    }

    /**
     * 현재 저장되어 있는 하루의 삼성 헬스 데이터를 가져오는 메소드(누적집계)
     * 해당 메소드를 이용하여 앱이 실행 될 때에 데이터를 가져올 수 있다.
     *
     * @author Alt
     * @since 2023-09-11
     */
    suspend fun aggregateWalk() {
        try {
            val startTime = LocalDate.now().atStartOfDay()
            val endTime = LocalDate.now().plusDays(1).atStartOfDay()
            Timber.d("startTime : $startTime")
            Timber.d("endTime: $endTime")
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = setOf(DataOrigin("com.sec.android.app.shealth"))
                )
            )
            response[StepsRecord.COUNT_TOTAL]?.let { walkTotal ->
                Timber.d("response : $walkTotal")
                mListener.sendSamsungHealthWalkData(walkTotal)
                mListener.checkedSamsungHealthStatus(SamsungHealthConnectStatus.CONNECT_OK.define)
            } ?: kotlin.run {
                Timber.d("currentData is Null!!")
                mListener.checkedSamsungHealthStatus(SamsungHealthConnectStatus.NOT_CONNECT.define)
            }

            // The result may be null if no data is available in the time range
        } catch (e: Exception) {
            // Run error handling here
            Timber.e("error Message : ${e.printStackTrace()}")
        }
    }

    /**
     * 상성헬스 설치 여부에 대한 검증 메소드
     *
     * @author Alt
     * @since 2023-09-14
     */
    fun isAvailableSamsungHealth(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val pi = context.packageManager.getPackageInfo(SAMSUNGHEALTH_PACKAGE,0)
            Timber.d("samsungHealth is Enabled?? : ${pi.applicationInfo.enabled}")
            pi.applicationInfo.enabled
        }catch (e: NameNotFoundException) {
            Timber.e("error : ${e.printStackTrace()}")
            false
        }
    }

    /**
     * 헬스 커넥트 관련 데이터 전달에 대한 리스너
     */
    interface HealthConnectResultListener {
        /**
         * 현재 삼성헬스 관련되어 앱 자체에 연결 되었는지 데이터 전달해주는 메소드
         *
         * @author Alt
         * @since 2023-09-14
         * @see SamsungHealthConnectStatus
         */
        fun checkedSamsungHealthStatus(status: Int)

        /**
         * 현재 저장된 삼성헬스 데이터를 전달하기 위하 메소드
         *
         * @author Alt
         * @since 2023-09-14
         */
        fun sendSamsungHealthWalkData(walkCount: Long)
    }
}