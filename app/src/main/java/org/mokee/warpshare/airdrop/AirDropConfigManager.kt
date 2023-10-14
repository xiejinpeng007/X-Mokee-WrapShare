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
package org.mokee.warpshare.airdrop

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import okio.ByteString
import org.mokee.warpshare.ConfigManager
import java.util.Random

internal class AirDropConfigManager(context: Context) {
    private val mParent: ConfigManager
    private val mPref: SharedPreferences

    init {
        mParent = ConfigManager(context)
        mPref = PreferenceManager.getDefaultSharedPreferences(context)
        if (!mPref.contains(KEY_ID)) {
            mPref.edit().putString(KEY_ID, generateId()).apply()
            Log.d(TAG, "Generate id: " + mPref.getString(KEY_ID, null))
        }
    }

    private fun generateId(): String {
        val id = ByteArray(6)
        Random().nextBytes(id)
        return ByteString.of(*id).hex()
    }

    val id: String?
        get() = mPref.getString(KEY_ID, null)
    val name: String
        get() = mParent.name
    val isDiscoverable: Boolean
        get() = mParent.isDiscoverable

    companion object {
        private const val TAG = "AirDropConfigManager"
        private const val KEY_ID = "airdrop_id"
    }
}
