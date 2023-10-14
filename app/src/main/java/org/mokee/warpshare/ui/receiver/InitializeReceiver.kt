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
import android.content.Intent
import android.util.Log
import org.mokee.warpshare.airdrop.AirDropManager
import org.mokee.warpshare.di.AppModule2

class InitializeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val airDropManager = AirDropManager(
            AppModule2.bleManager,
            AppModule2.wifiManager,
            AppModule2.certificateManager
        )
        airDropManager.registerTrigger(TriggerReceiver.getTriggerIntent(context))
        Log.d(TAG, "Initialized")
    }

    companion object {
        private const val TAG = "InitializeReceiver"
    }
}
