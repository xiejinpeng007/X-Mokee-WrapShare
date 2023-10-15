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

import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.net.InetAddress
import java.util.Locale
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

internal class AirDropNsdController(
    configManager: AirDropConfigManager,
    parent: AirDropManager,
    wifiManager: WifiManager,
) {
    private val mMulticastLock: MulticastLock
    private val mConfigManager: AirDropConfigManager
    private val mParent: AirDropManager
    private val mHandler = Handler(Looper.getMainLooper())
    private val mDiscoveryListener: ServiceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {}
        override fun serviceRemoved(event: ServiceEvent) {
            handleServiceLost(event.info)
        }

        override fun serviceResolved(event: ServiceEvent) {
            handleServiceResolved(event.info)
        }
    }
    private var mJmdns: JmDNS? = null
    private val mNetworkingThread: HandlerThread
    private val mNetworkingHandler: Handler

    init {
        mMulticastLock = wifiManager.createMulticastLock(TAG)
        mMulticastLock.setReferenceCounted(false)
        mNetworkingThread = HandlerThread("networking")
        mNetworkingThread.start()
        mNetworkingHandler = Handler(mNetworkingThread.looper)
        mConfigManager = configManager
        mParent = parent
    }

    fun destroy() {
        mNetworkingHandler.post {
            if (mJmdns != null) {
                try {
                    mJmdns!!.close()
                } catch (ignored: IOException) {
                }
                mJmdns = null
            }
            mNetworkingHandler.removeCallbacksAndMessages(null)
            mNetworkingThread.quit()
        }
    }

    private fun createJmdns(address: InetAddress) {
        // TODO: should handle address change
        if (mJmdns == null) {
            mJmdns = try {
                JmDNS.create(address)
            } catch (e: IOException) {
                throw RuntimeException("Failed creating JmDNS instance", e)
            }
        }
    }

    fun startDiscover(address: InetAddress) {
        mNetworkingHandler.post {
            mMulticastLock.acquire()
            createJmdns(address)
            mJmdns!!.addServiceListener(SERVICE_TYPE, mDiscoveryListener)
        }
    }

    fun stopDiscover() {
        mNetworkingHandler.post {
            if (mJmdns != null) {
                mJmdns!!.removeServiceListener(SERVICE_TYPE, mDiscoveryListener)
            }
            mMulticastLock.release()
        }
    }

    fun publish(address: InetAddress, port: Int) {
        mNetworkingHandler.post {
            createJmdns(address)
            val props: MutableMap<String, String?> = HashMap()
            props["flags"] = Integer.toString(
                FLAG_SUPPORTS_MIXED_TYPES or FLAG_SUPPORTS_DISCOVER_MAYBE
            )
            val serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                mConfigManager.id, port, 0, 0, props
            )
            Log.d(TAG, "Publishing $serviceInfo")
            try {
                mJmdns!!.registerService(serviceInfo)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun unpublish() {
        mNetworkingHandler.post { mJmdns!!.unregisterAllServices() }
    }

    private fun handleServiceLost(serviceInfo: ServiceInfo) {
        Log.d(TAG, "Disappeared: " + serviceInfo.name)
        postServiceLost(serviceInfo.name)
    }

    private fun handleServiceResolved(serviceInfo: ServiceInfo) {
        if (mConfigManager.id == serviceInfo.name) {
            return
        }
        Log.d(
            TAG, "Resolved: " + serviceInfo.name +
                    ", flags=" + serviceInfo.getPropertyString("flags")
        )
        val addresses = serviceInfo.inet4Addresses
        if (addresses.size > 0) {
            val url = String.format(
                Locale.US, "https://%s:%d",
                addresses[0].hostAddress, serviceInfo.port
            )
            postServiceResolved(serviceInfo.name, url)
        } else {
            Log.w(TAG, "No IPv4 address available, ignored: " + serviceInfo.name)
        }
    }

    private fun postServiceResolved(id: String, url: String) {
        mHandler.post { mParent.onServiceResolved(id, url) }
    }

    private fun postServiceLost(id: String) {
        mHandler.post { mParent.onServiceLost(id) }
    }

    companion object {
        private const val TAG = "AirDropNsdController"
        private const val SERVICE_TYPE = "_airdrop._tcp.local."
        private const val FLAG_SUPPORTS_MIXED_TYPES = 0x08
        private const val FLAG_SUPPORTS_DISCOVER_MAYBE = 0x80
    }
}
