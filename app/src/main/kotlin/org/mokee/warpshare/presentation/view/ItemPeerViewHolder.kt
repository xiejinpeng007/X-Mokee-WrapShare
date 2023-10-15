package org.mokee.warpshare.presentation.view

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.mokee.warpshare.databinding.ItemPeerBinding

class ItemPeerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var mBinding: ItemPeerBinding

    init {
        mBinding = ItemPeerBinding.bind(itemView)
    }
}