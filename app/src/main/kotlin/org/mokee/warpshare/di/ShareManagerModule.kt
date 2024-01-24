package org.mokee.warpshare.di

import org.mokee.warpshare.presentation.WarpShareApplication
import org.mokee.warpshare.core.airdrop.AirDropManager
import org.mokee.warpshare.domain.data.AirDropPeer
import org.mokee.warpshare.core.listener.DiscoverListener
import org.mokee.warpshare.domain.data.Entity
import org.mokee.warpshare.domain.data.Peer
import org.mokee.warpshare.core.listener.SendListener
import org.mokee.warpshare.core.listener.SendingSession
import org.mokee.warpshare.core.nearbysharing.NearShareManager
import org.mokee.warpshare.domain.data.NearSharePeer

object ShareManagerModule {
    val mAirDropManager by lazy {
        AirDropManager(AppModule.wifiManager, AppModule.certificateManager)
    }
    val mNearShareManager by lazy {
        NearShareManager(WarpShareApplication.instance)
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

    // I think we never need to do this
    fun destroy() {
        mAirDropManager.destroy()
        mNearShareManager.destroy()
    }
}