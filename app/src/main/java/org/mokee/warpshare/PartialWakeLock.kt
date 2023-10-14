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

import android.content.Context
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log

class PartialWakeLock(context: Context?, val tag: String) {
    private var mWakeLock: WakeLock? = null

    init {
        val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
        mWakeLock?.setReferenceCounted(false)
    }

    fun acquire() {
        Log.d(TAG, "$tag lock acquire")
        mWakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
    }

    fun release() {
        Log.d(TAG, "$tag lock release")
        mWakeLock?.release()
    }

    companion object {
        private const val TAG = "PartialWakeLock"
    }
}
