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
package org.mokee.warpshare.presentation.share

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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.mokee.warpshare.presentation.receiver.BluetoothStateMonitor
import org.mokee.warpshare.R
import org.mokee.warpshare.presentation.receiver.WifiStateMonitor
import org.mokee.warpshare.core.airdrop.AirDropManager
import org.mokee.warpshare.domain.data.Peer
import org.mokee.warpshare.databinding.FragmentShareBinding
import org.mokee.warpshare.presentation.isNightMode
import org.mokee.warpshare.presentation.view.PeersAdapter
import org.mokee.warpshare.presentation.main.MainViewModel
import org.mokee.warpshare.presentation.setup.SetupFragment

class ShareBottomSheetFragment : BottomSheetDialogFragment() {
    private var _mBinding: FragmentShareBinding? = null
    private val mBinding: FragmentShareBinding
        get() = _mBinding!!

    private val mViewModel by activityViewModels<MainViewModel>()
    private val mShareViewModel by viewModels<ShareViewModel>()

    private val mPeersAdapter = PeersAdapter(this::handleItemClick)

    private val mWifiStateMonitor: WifiStateMonitor = object : WifiStateMonitor() {
        override fun onReceive(context: Context, intent: Intent) {
            setupOrStartDiscovering()
        }
    }
    private val mBluetoothStateMonitor: BluetoothStateMonitor = object : BluetoothStateMonitor() {
        override fun onReceive(context: Context, intent: Intent) {
            setupOrStartDiscovering()
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
        Log.d(TAG, "onViewCreated night ${context?.isNightMode}")

        if (mShareViewModel.sendList.isEmpty()) {
            Log.w(TAG, "No file was selected")
            Toast.makeText(context, R.string.toast_no_file, Toast.LENGTH_SHORT).show()
            activity?.finish()
        }

        mBinding.recyclerViewPeers.adapter = mPeersAdapter
        mPeersAdapter.selectedPeer = mViewModel.mPeerPicked

        // config title
        val count = mShareViewModel.sendList.size
        val titleText = resources.getQuantityString(R.plurals.send_files_to, count, count)
        mBinding.title.text = titleText

        mBinding.btnSend.setOnClickListener {
            val peer = mViewModel.ensurePickedPeer() ?: return@setOnClickListener
            it.isEnabled = false
            mViewModel.sendFile(peer, mShareViewModel.sendList)
        }

        // Animate DragHandle
        (dialog as? BottomSheetDialog)?.behavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                val vb = _mBinding?: return
                val idleState =
                    newState != BottomSheetBehavior.STATE_DRAGGING && newState != BottomSheetBehavior.STATE_SETTLING
                var grabberAlpha = 1f
                if (idleState) {
                    grabberAlpha = 0.5f
                }
                vb.dragHandle.animate().alpha(grabberAlpha).setDuration(300)
                    .start()
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        })

        // Update Peer list
        mViewModel.peerListLiveData.observe(viewLifecycleOwner){
            mPeersAdapter.submitList(it)
        }

        // Update specific peer
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
        setupOrStartDiscovering()
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
        activity?.also {
            if (!it.isFinishing && !it.isChangingConfigurations) {
                mShareViewModel.peerState.cancelSend()
                activity?.finish()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _mBinding = null
    }

    private fun setupOrStartDiscovering() {
        if (mViewModel.mIsInSetup) return
        val ready = mViewModel.appModule.mAirDropManager.ready() == AirDropManager.STATUS_OK
        if (!ready) {
            SetupFragment.show(parentFragmentManager)
        } else {
            if (!mViewModel.mIsDiscovering) {
                mViewModel.mIsDiscovering = true
                mViewModel.appModule.startDiscover(mViewModel)
            }
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
