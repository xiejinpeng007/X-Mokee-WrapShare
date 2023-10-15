package org.mokee.warpshare.core.airdrop.server

import com.dd.plist.NSDictionary

interface ResultCallback {
    fun call(result: NSDictionary?)
}