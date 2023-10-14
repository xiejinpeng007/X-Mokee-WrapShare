package org.mokee.warpshare.airdrop

import java.io.InputStream

interface ReceiverListener {
    fun onAirDropRequest(session: ReceivingSession)
    fun onAirDropRequestCanceled(session: ReceivingSession)
    fun onAirDropTransfer(
        session: ReceivingSession,
        fileName: String?,
        input: InputStream?
    )

    fun onAirDropTransferProgress(
        session: ReceivingSession, fileName: String?,
        bytesReceived: Long, bytesTotal: Long,
        index: Int, count: Int
    )

    fun onAirDropTransferDone(session: ReceivingSession)
    fun onAirDropTransferFailed(session: ReceivingSession)
}