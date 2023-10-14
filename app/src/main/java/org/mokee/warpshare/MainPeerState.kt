package org.mokee.warpshare

import androidx.annotation.StringRes
import org.mokee.warpshare.base.SendingSession

data class MainPeerState(
    @StringRes
    var status: Int = 0,
    var bytesTotal: Long = -1,
    var bytesSent: Long = 0,
    var sending: SendingSession? = null,
){
    fun cancelSend(){
        sending?.cancel()
        sending = null
    }
}