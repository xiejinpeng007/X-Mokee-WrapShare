package org.mokee.warpshare.airdrop.server

import com.dd.plist.NSDictionary

interface ResultCallback {
    fun call(result: NSDictionary?)
}