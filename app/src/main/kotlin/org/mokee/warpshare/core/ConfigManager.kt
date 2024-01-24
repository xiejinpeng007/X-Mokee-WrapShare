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

import android.R.attr.capitalize
import android.os.Build
import android.provider.Settings
import org.mokee.warpshare.di.AppModule
import org.mokee.warpshare.presentation.WarpShareApplication


/**
 * 设置相关管理类
 */
object ConfigManager {

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        return if (model.startsWith(manufacturer)) {
            model.uppercase()
        } else {
            "${manufacturer.uppercase()}$model"
        }
    }


    /**
     * 默认名称 默认 设备名，没有直接 “Android”
     */
    val defaultName: String
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                Settings.Global.getString(WarpShareApplication.instance.contentResolver, Settings.Global.DEVICE_NAME) ?: getDeviceName()
            } else {
                getDeviceName()
            }
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
