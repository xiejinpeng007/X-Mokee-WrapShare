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
package org.mokee.warpshare.domain.data

import android.util.Log
import com.dd.plist.NSDictionary
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class AirDropPeer(
    override val id: String,
    override val name: String,
    override val status: PeerState = PeerState(),
    @JvmField val url: String,
    val capabilities: JsonObject?
) : Peer(id, name, status) {
    val mokeeApiVersion: Int
        get() {
            if (capabilities == null) {
                return 0
            }
            val vendor = capabilities.getAsJsonObject("Vendor") ?: return 0
            val mokee = vendor.getAsJsonObject("org.mokee") ?: return 0
            val api = mokee["APIVersion"] ?: return 0
            return api.asInt
        }

    override fun isTheSamePeer(other: Peer): Boolean {
        return if (other is AirDropPeer) {
            url == other.url
        } else {
            super.isTheSamePeer(other)
        }
    }

    companion object {
        private const val TAG = "AirDropPeer"

        @JvmStatic
        fun from(dict: NSDictionary, id: String, url: String): AirDropPeer? {
            val nameNode = dict["ReceiverComputerName"]
            if (nameNode == null) {
                Log.w(TAG, "Name is null: $id")
                return null
            }
            val name = nameNode.toJavaObject(String::class.java)
            var capabilities: JsonObject? = null
            val capabilitiesNode = dict["ReceiverMediaCapabilities"]
            if (capabilitiesNode != null) {
                val caps = capabilitiesNode.toJavaObject(ByteArray::class.java)
                try {
                    capabilities = JsonParser.parseString(String(caps)) as JsonObject
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing ReceiverMediaCapabilities", e)
                }
            }
            return AirDropPeer(id = id, name = name, url = url, capabilities = capabilities)
        }
    }
}
