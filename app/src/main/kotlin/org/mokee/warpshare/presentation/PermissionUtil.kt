package org.mokee.warpshare.presentation

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.mokee.warpshare.R
import org.mokee.warpshare.core.airdrop.AirDropManager
import org.mokee.warpshare.di.ShareManagerModule


class PermissionUtil(private val att: Activity, private val onRes: ((Map<String, Boolean>) -> Unit)? = null) :
    DefaultLifecycleObserver {
    private var mRequestBLELauncher31: ActivityResultLauncher<Array<String>>? = null
    private var mRequestBLELauncher: ActivityResultLauncher<Intent>? = null


    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        val caller = owner as? ActivityResultCaller

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val contract = ActivityResultContracts.RequestMultiplePermissions()
            mRequestBLELauncher31 = caller?.registerForActivityResult(contract) { result->
                if(result.values.contains(false)){
                    val isDenyForever = result.keys.map {
                        ActivityCompat.shouldShowRequestPermissionRationale(att, it)
                    }.count { !it } > 0
                    if(isDenyForever){
                        goAppSettingPage()
                    }else {
                        showPermissionDenyToast()
                    }
                }else {
                    requestToTurnOnBle()
                }
            }
        } else {
            mRequestBLELauncher =
                caller?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode != Activity.RESULT_OK) {
                        showPermissionDenyToast()
                    }
                }
        }
    }

    fun requestBLEPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkPermission(blePermissions)) {
                if (BluetoothAdapter.getDefaultAdapter().enable()) {
                    requestToTurnOnBle()
                }
            } else {
                mRequestBLELauncher31?.launch(blePermissions)
            }

        } else {
            requestToTurnOnBle()
        }
    }

    private fun goAppSettingPage(){
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = Uri.fromParts("package", WarpShareApplication.instance.packageName, null)
        }
        WarpShareApplication.instance.startActivity(intent)
    }

    private fun showPermissionDenyToast(){
        Toast.makeText(
            WarpShareApplication.instance,
            R.string.hint_perm,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun requestToTurnOnBle() {
        mRequestBLELauncher?.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        mRequestBLELauncher?.unregister()
        mRequestBLELauncher = null
    }

    companion object {
        const val TAG = "PermissionUtil"

        @JvmField
        val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            arrayOf()
        }

        /**
         * Check if all permissions are granted
         * @param permissions
         * @return true if all permissions are granted; false otherwise
         */
        @JvmStatic
        fun checkPermission(permissions: Array<String>): Boolean {
            val failedCount = permissions.map {
                val res = ContextCompat.checkSelfPermission(WarpShareApplication.instance, it)
                res == PackageManager.PERMISSION_GRANTED
            }.count { !it }
            return failedCount == 0
        }

        fun checkAirDropIsReady(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!checkPermission(blePermissions)) {
                    return AirDropManager.STATUS_NO_BLUETOOTH
                } else {
                    ShareManagerModule.mAirDropManager.ready()
                }
            } else {
                return ShareManagerModule.mAirDropManager.ready()
            }
        }
    }
}