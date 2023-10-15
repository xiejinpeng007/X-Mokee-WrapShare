package org.mokee.warpshare.presentation.view

import androidx.recyclerview.widget.DiffUtil
import org.mokee.warpshare.domain.data.Peer

class PeerItemDiffCallback : DiffUtil.ItemCallback<Peer>() {
    override fun areItemsTheSame(p0: Peer, p1: Peer): Boolean {
        return p0 == p1
    }

    override fun areContentsTheSame(p0: Peer, p1: Peer): Boolean {
        return p0.toString() == p1.toString()
    }
}