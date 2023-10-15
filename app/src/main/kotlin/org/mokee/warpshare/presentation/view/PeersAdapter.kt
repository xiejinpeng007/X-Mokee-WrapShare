package org.mokee.warpshare.presentation.view

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import org.mokee.warpshare.R
import org.mokee.warpshare.domain.data.AirDropPeer
import org.mokee.warpshare.domain.data.Peer
import org.mokee.warpshare.databinding.ItemPeerBinding
import org.mokee.warpshare.domain.data.PeerState
import org.mokee.warpshare.domain.data.NearSharePeer

class PeersAdapter(
    private val onItemClick: (Peer) -> Unit,
    private val onItemCancelClick: ((Peer) -> Unit)? = null,
    var selectedPeer: Peer? = null,
) : ListAdapter<Peer, ItemPeerViewHolder>(PeerItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemPeerViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ItemPeerViewHolder(inflater.inflate(R.layout.item_peer, parent, false))
    }

    override fun onBindViewHolder(holder: ItemPeerViewHolder, position: Int) {
        val peer = getItem(position)
        updateSimpleInfo(holder.mBinding, peer)

        updatePeerIcon(holder.mBinding, peer)

        holder.itemView.isSelected = peer.id == selectedPeer?.id
        holder.itemView.setOnClickListener { onItemClick(peer) }

        if (onItemCancelClick == null) {
            holder.mBinding.btnCancel.isVisible = false
        } else {
            holder.mBinding.btnCancel.setOnClickListener { onItemCancelClick.invoke(peer) }
        }
    }

    override fun onBindViewHolder(
        holder: ItemPeerViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if(payloads.isNotEmpty()){
            val msg = (payloads[0] as? String) ?:return
            when (msg) {
                "updatePeerInfo" -> {
                    val peer = getItem(position)
                    updateSimpleInfo(holder.mBinding, peer)
                }
                "updateSelectItem" -> {
                    val peer = getItem(position)
                    holder.itemView.isSelected = peer.id == selectedPeer?.id
                }
                else -> {

                }
            }
        }else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    //<editor-fold desc="UI update">
    private fun updateSimpleInfo(mBinding: ItemPeerBinding, peer: Peer){
        mBinding.textPeerName.text = peer.name
        updatePeerInfo(mBinding, peer)
    }

    private fun updatePeerInfo(mBinding: ItemPeerBinding, peer: Peer){
        val state = peer.status
        when (state.status) {
            R.string.status_waiting_for_confirm -> {
                enterConfirmState(mBinding)
            }
            R.string.status_sending -> {
                enterSendingState(mBinding, state)
            }
            R.string.status_rejected -> {
                enterRejectedState(mBinding)
            }
            R.string.toast_completed -> {
                enterIdleState(mBinding)
                mBinding.textStatus.setText(R.string.toast_completed);
            }
            else -> {
                enterIdleState(mBinding)
            }
        }
    }

    private fun updatePeerIcon(mBinding: ItemPeerBinding, peer: Peer) {
        val desireResId = if (peer is AirDropPeer) {
            val isMokee = peer.mokeeApiVersion > 0
            if (isMokee) {
                R.drawable.ic_mokee_24dp
            } else {
                R.drawable.ic_apple_24dp
            }
        } else if (peer is NearSharePeer) {
            R.drawable.ic_windows_24dp
        } else {
            0
        }
        mBinding.peerIcon.setImageResource(desireResId)
    }

    /**
     * 发送中状态
     */
    private fun enterSendingState(mBinding: ItemPeerBinding, state: PeerState) {
        mBinding.root.isEnabled = false
        mBinding.root.isSelected = true
        mBinding.progressBar.isVisible = true
        mBinding.progressBar.isIndeterminate = false
        mBinding.textStatus.isVisible = true
        mBinding.btnCancel.isVisible = true

        if (state.status == R.string.status_sending && state.bytesTotal != -1L) {
            val context = mBinding.root.context
            mBinding.progressBar.max = state.bytesTotal.toInt()
            mBinding.progressBar.progress = state.bytesSent.toInt()
            mBinding.textStatus.text = context.getString(
                R.string.status_sending_progress,
                Formatter.formatFileSize(context, state.bytesSent),
                Formatter.formatFileSize(context, state.bytesTotal)
            )
        } else {
            mBinding.textStatus.setText(state.status)
        }
    }

    /**
     * 空闲状态
     */
    private fun enterIdleState(mBinding: ItemPeerBinding) {
        mBinding.root.isEnabled = true
        mBinding.root.isSelected = false
        mBinding.progressBar.isVisible = false
        mBinding.textStatus.isVisible = false
        mBinding.btnCancel.isVisible = false
    }

    /**
     * 待确认状态
     */
    private fun enterConfirmState(mBinding:ItemPeerBinding){
        mBinding.root.isEnabled = false
        mBinding.root.isSelected = true
        mBinding.progressBar.isVisible = true
        mBinding.progressBar.isIndeterminate = true
        mBinding.textStatus.isVisible = true
        mBinding.textStatus.setText(R.string.status_waiting_for_confirm)
        mBinding.btnCancel.isVisible = true
    }

    /**
     * 被拒绝状态
     */
    private fun enterRejectedState(mBinding: ItemPeerBinding) {
        mBinding.root.isSelected = true
        mBinding.root.isEnabled = true
        mBinding.progressBar.isVisible = false
        mBinding.textStatus.isVisible = true
        mBinding.textStatus.setText(R.string.status_rejected)
        mBinding.btnCancel.isVisible = false
    }
    //</editor-fold>
}