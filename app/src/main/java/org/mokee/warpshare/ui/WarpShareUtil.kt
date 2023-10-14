package org.mokee.warpshare.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.mokee.warpshare.WarpShareApplication

object WarpShareUtil{

}


class PermissionUtil(private val onRes: ((Map<String, Boolean>) -> Unit)? = null):DefaultLifecycleObserver{
    private var mRequestBLELauncher31: ActivityResultLauncher<Array<String>>? = null
    private var mRequestBLELauncher:ActivityResultLauncher<Intent>? = null


    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        val caller = owner as? ActivityResultCaller

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            val contract = ActivityResultContracts.RequestMultiplePermissions()
            mRequestBLELauncher31 = caller?.registerForActivityResult(contract) {res ->
                requestToTurnOnBle()
            }
        }else {
            mRequestBLELauncher =
                caller?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        //granted
                    } else {
                        // TODO
                        Toast.makeText(
                            WarpShareApplication.getInstance(),
                            "Failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    fun requestBLEPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if(checkPermission(blePermissions)){
                if (BluetoothAdapter.getDefaultAdapter().enable()) {
                    requestToTurnOnBle()
                }
            }else{
                mRequestBLELauncher31?.launch(blePermissions)
            }

        }else{
            requestToTurnOnBle()
        }
    }

    private fun requestToTurnOnBle(){
        mRequestBLELauncher?.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        mRequestBLELauncher?.unregister()
        mRequestBLELauncher = null
    }

    companion object{

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
        fun checkPermission(permissions:Array<String>): Boolean {
            val failedCount = permissions.map {
                WarpShareApplication.getInstance().checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }.count { !it }
            return failedCount == 0
        }
    }
}