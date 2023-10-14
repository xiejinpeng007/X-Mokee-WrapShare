package org.mokee.warpshare.di

import org.mokee.warpshare.WarpShareApplication
import org.mokee.warpshare.airdrop.AirDropManager
import org.mokee.warpshare.airdrop.AirDropPeer
import org.mokee.warpshare.base.DiscoverListener
import org.mokee.warpshare.base.Entity
import org.mokee.warpshare.base.Peer
import org.mokee.warpshare.base.SendListener
import org.mokee.warpshare.base.SendingSession
import org.mokee.warpshare.nearbysharing.NearShareManager
import org.mokee.warpshare.nearbysharing.NearSharePeer

class AppModule(private val warpApp: WarpShareApplication) {
    val mAirDropManager by lazy {
        AirDropManager(warpApp, warpApp.certificateManager)
    }
    val mNearShareManager by lazy {
        NearShareManager(warpApp)
    }


    fun startDiscover(discoverListener: DiscoverListener) {
        mAirDropManager.startDiscover(discoverListener)
        mNearShareManager.startDiscover(discoverListener)
    }

    fun send(peer: Peer, entities: List<Entity>, listener: SendListener): SendingSession? {
        return when (peer) {
            is AirDropPeer -> {
                mAirDropManager.send(peer, entities, listener)
            }

            is NearSharePeer -> {
                mNearShareManager.send(peer, entities, listener)
            }
            else -> null
        }
    }

    fun stopDiscover() {
        mAirDropManager.stopDiscover()
        mNearShareManager.stopDiscover()
    }

    fun destroy() {
        mAirDropManager.destroy()
        mNearShareManager.destroy()
    }
}