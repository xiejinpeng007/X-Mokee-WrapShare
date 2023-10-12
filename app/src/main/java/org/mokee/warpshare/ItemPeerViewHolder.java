package org.mokee.warpshare;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.mokee.warpshare.databinding.ItemPeerMainBinding;

public class ItemPeerViewHolder extends RecyclerView.ViewHolder {
    ItemPeerMainBinding mBinding;
    public ItemPeerViewHolder(@NonNull View itemView) {
        super(itemView);
        mBinding = ItemPeerMainBinding.bind(itemView);
    }
}
