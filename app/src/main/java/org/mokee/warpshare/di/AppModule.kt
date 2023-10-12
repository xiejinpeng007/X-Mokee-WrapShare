package org.mokee.warpshare.di

import org.mokee.warpshare.WarpShareApplication
import org.mokee.warpshare.airdrop.AirDropManager
import org.mokee.warpshare.nearbysharing.NearShareManager

class AppModule(private val warpApp: WarpShareApplication) {
    val mAirDropManager by lazy {
        AirDropManager(warpApp, warpApp.certificateManager)
    }
    val mNearShareManager by lazy {
        NearShareManager(warpApp)
    }

    fun destroy(){
        mAirDropManager.destroy()
        mNearShareManager.destroy()
    }
}