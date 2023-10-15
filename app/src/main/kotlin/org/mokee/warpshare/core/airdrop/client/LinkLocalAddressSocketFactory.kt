package org.mokee.warpshare.core.airdrop.client

import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

class LinkLocalAddressSocketFactory : SocketFactory() {
    private var mInterface: NetworkInterface? = null
    override fun createSocket(): Socket {
        return object : Socket() {
            override fun connect(socketAddress: SocketAddress, timeout: Int) {
                var tempSocketAddress = socketAddress
                if (mInterface != null && tempSocketAddress is InetSocketAddress) {
                    if (tempSocketAddress.address is Inet6Address) {
                        val address = tempSocketAddress.address as Inet6Address

                        // OkHttp can't parse link-local IPv6 addresses properly, so we need to
                        // set the scope of the address here
                        val addressWithScope: InetAddress = Inet6Address.getByAddress(
                            null, address.address, mInterface
                        )
                        tempSocketAddress = InetSocketAddress(addressWithScope, tempSocketAddress.port)
                    }
                }
                super.connect(tempSocketAddress, timeout)
            }
        }
    }

    override fun createSocket(host: String, port: Int): Socket? {
        return null
    }

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int
    ): Socket? {
        return null
    }

    override fun createSocket(host: InetAddress, port: Int): Socket? {
        return null
    }

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket? {
        return null
    }
}