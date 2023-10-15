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
package org.mokee.warpshare.core

import org.mokee.warpshare.di.AppModule
import org.mokee.warpshare.presentation.PermissionUtil
import org.mokee.warpshare.presentation.PermissionUtil.Companion.checkPermission

/**
 * 设置相关管理类
 */
object ConfigManager {

    private val bluetoothAdapterName: String?
        get() {
            val adapter = AppModule.bleManager.adapter ?: return null
            return if (!checkPermission(PermissionUtil.blePermissions)) {
                null
            } else {
                adapter.name
            }
        }

    /**
     * 默认名称 默认 设备名，没有直接 “Android”
     */
    val defaultName: String
        get() {
            return bluetoothAdapterName.takeIf { !it.isNullOrBlank() } ?: "Android"
        }

    /**
     * 自定义名称????
     */
    val nameWithoutDefault: String?
        get() = AppModule.mPref.getString(KEY_NAME, "")

    /**
     * 自定义名称 或者 默认名称
     */
    val name: String
        get() {
            return nameWithoutDefault.takeIf { !it.isNullOrBlank() } ?: defaultName
        }
    val isDiscoverable: Boolean
        get() = AppModule.mPref.getBoolean(KEY_DISCOVERABLE, false)

    const val KEY_NAME = "name"
    const val KEY_DISCOVERABLE = "discoverable"
    private const val TAG = "ConfigManager"
}
