package org.mokee.warpshare

import android.os.Build
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.mokee.warpshare.airdrop.AirDropPeer
import org.mokee.warpshare.base.Peer
import org.mokee.warpshare.nearbysharing.NearSharePeer

class PeersAdapter(
    private val onItemClick:(Peer)->Unit,
    private val onItemCancelClick:((Peer)->Unit)? = null,
) : ListAdapter<Peer,ItemPeerViewHolder>(PeerItemCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemPeerViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ItemPeerViewHolder(inflater.inflate(R.layout.item_peer_main, parent, false))
    }

    override fun onBindViewHolder(holder: ItemPeerViewHolder, position: Int) {
        val peer = getItem(position)
        val state = peer.status
        val itemViewContext = holder.itemView.context
        holder.mBinding.name.text = peer.name
        if (state.status != 0) {
            holder.itemView.isSelected = true
            holder.mBinding.status.visibility = View.VISIBLE
            if (state.status == R.string.status_sending && state.bytesTotal != -1L) {
                holder.mBinding.status.text = itemViewContext.getString(
                    R.string.status_sending_progress,
                    Formatter.formatFileSize(itemViewContext, state.bytesSent),
                    Formatter.formatFileSize(itemViewContext, state.bytesTotal)
                )
            } else {
                holder.mBinding.status.setText(state.status)
            }
        } else {
            holder.itemView.isSelected = false
            holder.mBinding.status.visibility = View.GONE
        }
        if (state.status != 0 && state.status != R.string.status_rejected) {
            holder.itemView.setEnabled(false)
            holder.mBinding.progress.visibility = View.VISIBLE
            holder.mBinding.cancel.setVisibility(View.VISIBLE)
            if (state.bytesTotal == -1L || state.status != R.string.status_sending) {
                holder.mBinding.progress.isIndeterminate = true
            } else {
                holder.mBinding.progress.isIndeterminate = false
                holder.mBinding.progress.setMax(state.bytesTotal.toInt())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // TODO
                    holder.mBinding.progress.setProgress(state.bytesSent.toInt(), true)
                }
            }
        } else {
            holder.itemView.setEnabled(true)
            holder.mBinding.progress.visibility = View.GONE
            holder.mBinding.cancel.setVisibility(View.GONE)
        }
        if (peer is AirDropPeer) {
            val isMokee = peer.mokeeApiVersion > 0
            if (isMokee) {
                holder.mBinding.icon.setImageResource(R.drawable.ic_mokee_24dp)
            } else {
                holder.mBinding.icon.setImageResource(R.drawable.ic_apple_24dp)
            }
        } else if (peer is NearSharePeer) {
            holder.mBinding.icon.setImageResource(R.drawable.ic_windows_24dp)
        } else {
            holder.mBinding.icon.setImageDrawable(null)
        }
        holder.itemView.setOnClickListener {  onItemClick(peer) }
        if(onItemCancelClick == null){
            holder.mBinding.cancel.isVisible = false
        }else {
            holder.mBinding.cancel.setOnClickListener { onItemCancelClick.invoke(peer) }
        }
    }

}