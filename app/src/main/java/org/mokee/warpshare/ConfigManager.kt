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
package org.mokee.warpshare

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import androidx.preference.PreferenceManager
import org.mokee.warpshare.ui.PermissionUtil
import org.mokee.warpshare.ui.PermissionUtil.Companion.checkPermission

class ConfigManager(private val mContext: Context) {
    private val mPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)

    private val bluetoothAdapterName: String?
        get() {
            val manager = mContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: return null
            return if (!checkPermission(PermissionUtil.blePermissions)) {
                null
            } else {
                adapter.name
            }
        }
    val defaultName: String
        get() {
            val name = bluetoothAdapterName
            return if (TextUtils.isEmpty(name)) "Android" else name!!
        }
    val nameWithoutDefault: String?
        get() = mPref.getString(KEY_NAME, "")
    val name: String
        get() {
            val name = nameWithoutDefault
            return if (TextUtils.isEmpty(name)) defaultName else name!!
        }
    val isDiscoverable: Boolean
        get() = mPref.getBoolean(KEY_DISCOVERABLE, false)

    companion object {
        const val KEY_NAME = "name"
        const val KEY_DISCOVERABLE = "discoverable"
        private const val TAG = "ConfigManager"
    }
}
