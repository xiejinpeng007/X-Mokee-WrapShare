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

import android.util.Log
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.UnknownHostException

internal class AirDropWlanController {
    private val mLock = Any()
    private var mInterface: NetworkInterface? = null
    private var mLocalAddress: InetAddress? = null
    private val localAddressInternal: Unit
        get() {
            var iface: NetworkInterface? = null
            var address: InetAddress? = null
            for (name in INTERFACES) {
                iface = try {
                    NetworkInterface.getByName(name)
                } catch (e: SocketException) {
                    Log.w(TAG, "Failed getting interface $name", e)
                    continue
                }
                if (iface == null) {
                    Log.w(TAG, "Failed getting interface $name")
                    continue
                }
                address = null
                val addresses = iface.inetAddresses
                var address6: Inet6Address? = null
                var address4: Inet4Address? = null
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (address6 == null && addr is Inet6Address) {
                        try {
                            // Recreate a non-scoped address since we are going to advertise it out
                            address6 =
                                Inet6Address.getByAddress(null, addr.getAddress()) as Inet6Address
                        } catch (ignored: UnknownHostException) {
                        }
                    } else if (address4 == null && addr is Inet4Address) {
                        address4 = addr
                    }
                }
                if (address4 != null) {
                    address = address4
                } else if (address6 != null) {
                    address = address6
                }
                if (address != null) {
                    break
                }
            }
            if (iface == null) {
                Log.e(TAG, "No available interface found")
                mInterface = null
                mLocalAddress = null
            } else if (address == null) {
                Log.e(TAG, "No address available for interface " + iface.name)
                mInterface = null
                mLocalAddress = null
            } else {
                Log.d(TAG, "Found available interface " + iface.name + ", " + address.hostAddress)
                mInterface = iface
                mLocalAddress = address
            }
        }

    fun ready(): Boolean {
        synchronized(mLock) {
            localAddressInternal
            return mInterface != null && mLocalAddress != null
        }
    }

    val networkInterface: NetworkInterface?
        get() {
            synchronized(mLock) {
                localAddressInternal
                if (mInterface == null) {
                    return null
                }
            }
            return mInterface
        }
    val localAddress: InetAddress?
        get() {
            synchronized(mLock) {
                localAddressInternal
                if (mLocalAddress == null) {
                    return null
                }
            }
            return mLocalAddress
        }

    companion object {
        private const val TAG = "AirDropWlanController"
        private val INTERFACES = arrayOf("wlan1", "wlan0")
    }
}
