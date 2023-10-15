package org.mokee.warpshare.core.airdrop.client

import com.dd.plist.NSDictionary
import okio.IOException

internal interface AirDropClientCallback {
    fun onFailure(e: IOException)
    fun onResponse(response: NSDictionary)
}