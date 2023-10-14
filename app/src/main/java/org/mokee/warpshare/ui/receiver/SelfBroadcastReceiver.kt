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
package org.mokee.warpshare.ui.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

abstract class SelfBroadcastReceiver(vararg actions: String?) : BroadcastReceiver() {
    private val mIntentFilter = IntentFilter()
    private var mRegistered = false

    init {
        for (action in actions) {
            mIntentFilter.addAction(action)
        }
    }

    fun register(context: Context) {
        synchronized(this) {
            if (!mRegistered) {
                context.registerReceiver(this, mIntentFilter)
                mRegistered = true
            }
        }
    }

    fun unregister(context: Context) {
        synchronized(this) {
            if (mRegistered) {
                context.unregisterReceiver(this)
                mRegistered = false
            }
        }
    }
}
