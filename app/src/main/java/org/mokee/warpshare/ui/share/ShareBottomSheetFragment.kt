/*
 * Copyright (C) 2019 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mokee.warpshare.ui.share

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.mokee.warpshare.ui.receiver.BluetoothStateMonitor
import org.mokee.warpshare.R
import org.mokee.warpshare.ui.receiver.WifiStateMonitor
import org.mokee.warpshare.airdrop.AirDropManager
import org.mokee.warpshare.domain.data.Peer
import org.mokee.warpshare.databinding.FragmentShareBinding
import org.mokee.warpshare.ui.PeersAdapter
import org.mokee.warpshare.ui.main.MainViewModel
import org.mokee.warpshare.ui.setup.SetupFragment

class ShareBottomSheetFragment : BottomSheetDialogFragment() {
    private var _mBinding: FragmentShareBinding? = null
    private val mBinding: FragmentShareBinding
        get() = _mBinding!!

    private val mViewModel by viewModels<MainViewModel>()
    private val mShareViewModel by viewModels<ShareViewModel>()

    private val mPeersAdapter = PeersAdapter(this::handleItemClick)

    private val mWifiStateMonitor: WifiStateMonitor = object : WifiStateMonitor() {
        override fun onReceive(context: Context, intent: Intent) {
            setupIfNeeded()
        }
    }
    private val mBluetoothStateMonitor: BluetoothStateMonitor = object : BluetoothStateMonitor() {
        override fun onReceive(context: Context, intent: Intent) {
            setupIfNeeded()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _mBinding = FragmentShareBinding.inflate(inflater, container, false)
        return mBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (mShareViewModel.sendList.isEmpty()) {
            Log.w(tag, "No file was selected")
            Toast.makeText(context, R.string.toast_no_file, Toast.LENGTH_SHORT).show()
            activity?.finish()
        }

        mBinding.recyclerViewPeers.adapter = mPeersAdapter

        // config title
        val count = mShareViewModel.sendList.size
        val titleText = resources.getQuantityString(R.plurals.send_files_to, count, count)
        mBinding.title.text = titleText

        mBinding.btnSend.setOnClickListener {
            val peer = mViewModel.ensurePickedPeer() ?: return@setOnClickListener
            it.isEnabled = false
            mViewModel.sendFile(peer, mShareViewModel.sendList)
        }

        mViewModel.peerListLiveData.observe(viewLifecycleOwner){
            mPeersAdapter.submitList(it)
        }

        lifecycleScope.launch {
            mViewModel.peerUpdateFlow.flowWithLifecycle(viewLifecycleOwner.lifecycle).collect {
                updatePeerInfo(it)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        context?.also{
            mWifiStateMonitor.register(it)
            mBluetoothStateMonitor.register(it)
        }
        if (setupIfNeeded()) {
            return
        }
        if (!mViewModel.mIsDiscovering) {
            mViewModel.mIsDiscovering = true
            mViewModel.appModule.startDiscover(mViewModel)
        }
    }

    override fun onPause() {
        super.onPause()
        if (mViewModel.mIsDiscovering && !mViewModel.mShouldKeepDiscovering) {
            mViewModel.mIsDiscovering = false
            mViewModel.appModule.stopDiscover()
        }
        context?.also{
            mWifiStateMonitor.unregister(it)
            mBluetoothStateMonitor.unregister(it)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mShareViewModel.peerState.cancelSend()
        activity?.finish()
    }

    private fun setupIfNeeded(): Boolean {
        if (mViewModel.mIsInSetup) {
            return true
        }
        val ready = mViewModel.appModule.mAirDropManager.ready() == AirDropManager.STATUS_OK
        return if (!ready) {
            SetupFragment.show(parentFragmentManager)
            true
        } else {
            false
        }
    }

    private fun handleItemClick(peer: Peer) {
        mShareViewModel.peerState.apply {
            // is sending or confirming ==> return
            if(status != 0 && status != R.string.status_rejected) {
                return
            }
        }

        val previousPeer = mViewModel.mPeerPicked

        mShareViewModel.peerState.status = 0
        if (peer.id == mViewModel.mPeerPicked?.id) {
            mViewModel.mPeerPicked = null
            mBinding.btnSend.isEnabled = false
        } else {
            mViewModel.mPeerPicked = peer
            mBinding.btnSend.isEnabled = true
        }

        highlightSelectedItem(previousPeer, mViewModel.mPeerPicked)
    }

    /**
     * update UI to highlight selected item
     */
    private fun highlightSelectedItem(previousPeer:Peer?, currentPeer:Peer?){
        mPeersAdapter.selectedPeer = currentPeer
        mPeersAdapter.currentList.forEachIndexed { index, peer ->
            val isPreviousSelected = peer.id == previousPeer?.id
            val shouldSelect = peer.id == currentPeer?.id
            if(shouldSelect != isPreviousSelected){
                mPeersAdapter.notifyItemChanged(index, "updateSelectItem")
            }
        }
    }

    /**
     * Update the correspond Peer in the RecyclerView
     */
    private fun updatePeerInfo(peer: Peer?) {
        if(peer == null) return
        val index = mPeersAdapter.currentList.indexOfFirst { it.id == peer.id }
        if(index == -1) return
        mPeersAdapter.notifyItemChanged(index, "updatePeerInfo")

        mShareViewModel.peerState.update(peer.status)
        if(peer.status.status == R.string.toast_completed){
            val ctx = context ?: return
            Toast.makeText(ctx, R.string.toast_completed, Toast.LENGTH_SHORT).show()
            _mBinding?.root?.postDelayed({
                dismiss()
            }, 1000L) ?: dismiss()
        }
    }

    companion object {
        const val TAG = "ShareBottomSheetFragment"
        fun show(fragmentManager: FragmentManager, intent: Intent) {
            val fg = ShareBottomSheetFragment()
            fg.arguments = Bundle().apply {
                putParcelable("clipData", intent.clipData)
                putString("type", intent.type)
            }
            fg.show(fragmentManager, TAG)
        }
    }
}
