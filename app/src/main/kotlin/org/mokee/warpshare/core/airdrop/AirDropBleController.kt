/*
 * Copyright (C) 2019 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mokee.warpshare.core.airdrop

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.util.Log
import org.mokee.warpshare.presentation.PermissionUtil
import org.mokee.warpshare.presentation.PermissionUtil.Companion.checkPermission

internal class AirDropBleController(private val bleManager: BluetoothManager) {
    private val mAdvertiseCallback: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            handleAdvertiseStartSuccess()
        }

        override fun onStartFailure(errorCode: Int) {
            handleAdvertiseStartFailure(errorCode)
        }
    }
    private var mAdapter: BluetoothAdapter? = null

    private val adapter: BluetoothAdapter?
        get() {
            if (mAdapter != null) return mAdapter

            val adapter = bleManager.adapter
            return if (adapter == null || !adapter.isEnabled) {
                null
            } else {
                mAdapter = adapter
                adapter
            }
        }
    private val mAdvertiser: BluetoothLeAdvertiser?
        get() {
            return adapter?.bluetoothLeAdvertiser
        }

    private val mScanner: BluetoothLeScanner?
        get() {
            return adapter?.bluetoothLeScanner
        }

    @Synchronized
    fun ready(): Boolean {
        return mAdvertiser != null && mScanner != null
    }

    @Synchronized
    fun triggerDiscoverable() {
        if (!checkPermission(PermissionUtil.blePermissions)) {
            return
        }
        if (mAdvertiser == null) {
            return
        }
        mAdvertiser?.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build(),
            AdvertiseData.Builder()
                .addManufacturerData(MANUFACTURER_ID, MANUFACTURER_DATA)
                .build(),
            mAdvertiseCallback
        )
    }

    @Synchronized
    fun stop() {
        if (mAdvertiser == null) {
            return
        }
        if (checkPermission(PermissionUtil.blePermissions)) {
            mAdvertiser?.stopAdvertising(mAdvertiseCallback)
        }
    }

    private val filters = arrayListOf(
        ScanFilter.Builder()
            .setManufacturerData(MANUFACTURER_ID, MANUFACTURER_DATA, MANUFACTURER_DATA)
            .build()
    )

    private val scanSettings = ScanSettings.Builder()
        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH or ScanSettings.CALLBACK_TYPE_MATCH_LOST)
        .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    @Synchronized
    fun registerTrigger(pendingIntent: PendingIntent) {
        if (mScanner == null) {
            return
        }
        if (!checkPermission(PermissionUtil.blePermissions)) {
            return
        }
        if(adapter?.isEnabled != true){
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            mScanner?.startScan(filters, scanSettings, object : ScanCallback() {
//                override fun onScanResult(callbackType: Int, result: ScanResult) {
//                    super.onScanResult(callbackType, result)
//                }
//            })
            mScanner?.startScan(filters, scanSettings, pendingIntent)
        } else {
            mScanner?.startScan(filters, scanSettings, object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                }
            })
        }
        Log.d(TAG, "startScan")
    }

    private fun handleAdvertiseStartSuccess() {
        Log.d(TAG, "Start advertise succeed")
    }

    private fun handleAdvertiseStartFailure(errorCode: Int) {
        Log.e(TAG, "Start advertise failed: $errorCode")
    }

    companion object {
        private const val TAG = "AirDropBleController"
        private const val MANUFACTURER_ID = 0x004C
        private val MANUFACTURER_DATA = byteArrayOf(
            0x05, 0x12, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        )
    }
}
