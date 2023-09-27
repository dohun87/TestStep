package com.neibus.teststep

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.neibus.teststep.databinding.ActivityMainBinding
import com.neibus.teststep.define.IntentDefine
import com.neibus.teststep.module.HealthConnectModule
import com.neibus.teststep.module.PreferencesModule
import com.neibus.teststep.module.WalkModule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), HealthConnectModule.HealthConnectResultListener {

    @Inject lateinit var mHealthConnectModule: HealthConnectModule
    @Inject lateinit var mPrefModule: PreferencesModule
    @Inject lateinit var mWalkModule: WalkModule

    private lateinit var binding: ActivityMainBinding
    private var isNotInstall = false
    private var isNotPermission = false
    private lateinit var mSnackBar: Snackbar

    private var isUsableSamsungHealth = false
    private var isSamsungHealthConnectStatus = false

    private val requestActivitiesPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted && !isActiveService()) {
            executeWalkService()
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            Toast.makeText(this,"권한 획득에 실패하였습니다",Toast.LENGTH_SHORT).show()
        }
    }

    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        Timber.d("Notification Permisson : $isGranted")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkedActivitesPermission()
        } else {
            executeWalkService()
        }


        if(mHealthConnectModule.isAvailableSamsungHealth() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mHealthConnectModule.setListener(this)
            isUsableSamsungHealth = true
            initGoogleHealthConnect()
        } else {
            isUsableSamsungHealth = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if(intent.hasExtra(IntentDefine.SEND_WALK_COUNT.action)) {
            val currentCount = intent.getIntExtra(IntentDefine.SEND_WALK_COUNT.action,0)
            Timber.d("currentCount: $currentCount")
            binding.tvCurrentWalk.text = String.format(getString(R.string.format_current_walk),currentCount)
        }
    }

    override fun onResume() {
        super.onResume()
        if(isUsableSamsungHealth) {
            if(isNotInstall) {
                if(!mHealthConnectModule.isHealConnectInstalled()) {
                    showHealthConnectSnackbar(
                        getString(R.string.health_connect_install_guide),
                        HealthConnectModule.InitResultType.NOT_INSTALL.define
                    )
                } else {
                    isNotInstall = false
                    lifecycleScope.launch {
                        mHealthConnectModule.checkPermissionsAndRun()
                    }
                }
            }

            if(isNotPermission) {
                showHealthConnectSnackbar(
                    getString(R.string.health_connect_permission_guide),
                    HealthConnectModule.InitResultType.NOT_PERMISSION.define
                )
                return
            }
            if(!isSamsungHealthConnectStatus) {
                //mReconnectHandler.sendEmptyMessage(0)
            }
        }
    }

    override fun onBackPressed() {
        finishAffinity()
    }

    private fun executeWalkService() {
        val intent = Intent(this, WalkService::class.java)
        intent.action = IntentDefine.WALK_START.action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkedActivitesPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            requestActivitiesPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            if(!isActiveService()) {
                executeWalkService()
            }
        }
    }

    /**
     * 헬스커넥트 모듈에 대한 초기화 메소드
     * 삼성헬스가 설치되어 있으며 파이 이상일 때에만 동작 되게 되있다.
     *
     * @author Alt
     * @since 2023-09-14
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun initGoogleHealthConnect() {
        mHealthConnectModule.availHealthConnectStatus(this) {
            when(it) {
                HealthConnectModule.InitResultType.NOT_USABLE.define -> {
                    Toast.makeText(this,"해당 기기서는 구글 헬스커넥트를 사용할 수 없습니다.",Toast.LENGTH_SHORT).show()
                }

                HealthConnectModule.InitResultType.NOT_INSTALL.define -> {
                    lifecycleScope.launch {
                        isNotInstall = true
                        showHealthConnectSnackbar(getString(R.string.health_connect_install_guide),it)
                    }
                }
                HealthConnectModule.InitResultType.NOT_PERMISSION.define -> {
                    lifecycleScope.launch {
                        isNotPermission = true
                        showHealthConnectSnackbar(getString(R.string.health_connect_permission_guide),it)
                    }
                }
                HealthConnectModule.InitResultType.PERMISSION_OK.define -> {
                    Timber.d("헬스커넥트 설치 되었다!!")
                    isNotPermission = false
                    if(::mSnackBar.isInitialized && mSnackBar.isShown) {
                        mSnackBar.dismiss()
                    }
                }
            }
        }
    }



    /**
     * 삼성헬스 데이터를 가져오기 위한 메소드
     * 만약 처음 기기 연결 시 삼성헬스 가져오지 못할 경우에도 해당 내역을 이용해서 진행한다.
     *
     * @author Alt
     * @since 2023-09-14
     */
    private fun executeSamsungHealthData() {
        lifecycleScope.launch {
            mHealthConnectModule.getDistanceData()
            mHealthConnectModule.aggregateWalk()
        }
    }


    private fun showHealthConnectSnackbar(message: String, define: Int) {
        val btnText = getString(R.string.btn_setting)
        mSnackBar = Snackbar.make(binding.root,message,Snackbar.LENGTH_INDEFINITE).setAction(btnText, View.OnClickListener {
            if(define == HealthConnectModule.InitResultType.NOT_INSTALL.define) {
                moveHealthConnectPlayStore()
            } else {
                mHealthConnectModule.executePermissions()
            }

        })
        mSnackBar.show()
    }

    private fun moveHealthConnectPlayStore() {
        val uriString = "market://details?id=com.google.android.apps.healthdata&url=healthconnect://onboarding"
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.android.vending")
                data = Uri.parse(uriString)
                putExtra("overlay", true)
                putExtra("callerId", packageName)
            }
        )
    }

    /**
     * 현재 서비스가 동작중인지 확인하기 위한 메소드
     *
     * @author Alt
     */
    @Suppress("DEPRECATION")
    private fun isActiveService(): Boolean {
        val manager = getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (WalkService::class.java.name.equals(service.service.className)) {
                return true
            }
        }
        return false
    }


    override fun checkedSamsungHealthStatus(status: Int) {
        Timber.d("current Device SamsungHealthStatus: $status")
        isSamsungHealthConnectStatus = if(status == HealthConnectModule.SamsungHealthConnectStatus.CONNECT_OK.define) {
            true
        } else {
//            if(!mReconnectHandler.hasMessages(0)) {
//                Timber.d("SamsungHealth Not Connect Status = ready ReconnectHandler")
//                mReconnectHandler.sendEmptyMessageDelayed(0,30000L)
//            }
            false
        }
    }

    override fun sendSamsungHealthWalkData(walkCount: Long) {
        Timber.d("getSamsungHealthWalkData: $walkCount")
        binding.tvSamsung.text = String.format(getString(R.string.format_samsung),walkCount)
        mWalkModule.setSamsungHealthData(walkCount.toInt())
    }
}