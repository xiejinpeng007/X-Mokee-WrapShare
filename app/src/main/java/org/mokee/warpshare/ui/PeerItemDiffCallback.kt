package org.mokee.warpshare.ui

import androidx.recyclerview.widget.DiffUtil
import org.mokee.warpshare.base.Peer

class PeerItemDiffCallback : DiffUtil.ItemCallback<Peer>() {
    override fun areItemsTheSame(p0: Peer, p1: Peer): Boolean {
        return p0 == p1
    }

    override fun areContentsTheSame(p0: Peer, p1: Peer): Boolean {
        return p0.toString() == p1.toString()
    }
}