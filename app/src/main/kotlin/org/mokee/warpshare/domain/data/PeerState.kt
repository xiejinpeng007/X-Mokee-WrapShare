package org.mokee.warpshare.domain.data

import androidx.annotation.StringRes
import org.mokee.warpshare.base.SendingSession

/**
 * Peer's current state info
 *
 * @param status Peer's current state, maybe:
 * * idle: 0
 * * sending: [org.mokee.warpshare.R.string.status_sending]
 * * rejected: [org.mokee.warpshare.R.string.status_rejected]
 * @param bytesTotal Total bytes to send
 * @param bytesSent Bytes sent
 * @param sending Sending session
 */
data class PeerState(
    @StringRes
    var status: Int = 0,
    var bytesTotal: Long = -1,
    var bytesSent: Long = 0,
    var sending: SendingSession? = null,
){
    fun update(newState: PeerState){
        status = newState.status
        bytesTotal = newState.bytesTotal
        bytesSent = newState.bytesSent
        sending = newState.sending
    }

    fun cancelSend(){
        sending?.cancel()
        sending = null
    }
}