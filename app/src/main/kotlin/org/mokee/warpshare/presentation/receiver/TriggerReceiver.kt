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
package org.mokee.warpshare.presentation.receiver

import android.app.PendingIntent
import android.app.PendingIntent.CanceledException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.mokee.warpshare.presentation.service.ReceiverService

class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive")
        val callbackIntent = intent.getParcelableExtra<PendingIntent>(EXTRA_CALLBACK_INTENT)
        try {
            callbackIntent!!.send(context, 0, intent)
        } catch (e: CanceledException) {
            Log.e(TAG, "Failed sending callback", e)
        }
    }

    companion object {
        const val TAG = "TriggerReceiver"
        const val EXTRA_CALLBACK_INTENT = "callback"
        @JvmStatic
        fun getTriggerIntent(context: Context?): PendingIntent {
            val callbackIntent = ReceiverService.getTriggerIntent(context)
            val intent = Intent(context, TriggerReceiver::class.java)
            intent.putExtra(EXTRA_CALLBACK_INTENT, callbackIntent)
            return PendingIntent.getBroadcast(
                context, callbackIntent.hashCode(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
