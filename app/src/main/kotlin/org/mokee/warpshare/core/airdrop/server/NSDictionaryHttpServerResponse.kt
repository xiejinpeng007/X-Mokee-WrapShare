package org.mokee.warpshare.core.airdrop.server

import com.dd.plist.NSDictionary

internal interface NSDictionaryHttpServerResponse {
    fun send(code: Int)
    fun send(nsDictionary: NSDictionary?)
}