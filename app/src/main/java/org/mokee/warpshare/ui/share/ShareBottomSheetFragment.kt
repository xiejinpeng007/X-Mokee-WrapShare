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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.mokee.warpshare.BluetoothStateMonitor
import org.mokee.warpshare.PartialWakeLock
import org.mokee.warpshare.R
import org.mokee.warpshare.WifiStateMonitor
import org.mokee.warpshare.airdrop.AirDropManager
import org.mokee.warpshare.base.Peer
import org.mokee.warpshare.databinding.FragmentShareBinding
import org.mokee.warpshare.ui.PeersAdapter
import org.mokee.warpshare.ui.main.MainViewModel
import org.mokee.warpshare.ui.setup.SetupFragment

class ShareBottomSheetFragment : BottomSheetDialogFragment() {
    private var _mBinding: FragmentShareBinding? = null
    private val mBinding: FragmentShareBinding
        get() = _mBinding!!

    private var mWakeLock: PartialWakeLock? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mWakeLock = PartialWakeLock(context, TAG)
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
            val peer = mViewModel.getSelectedPeer() ?: return@setOnClickListener
            mViewModel.sendFile(peer, mShareViewModel.sendList)
        }

        mViewModel.peerListLiveData.observe(viewLifecycleOwner){
            mPeersAdapter.submitList(it)
        }
    }

    override fun onResume() {
        super.onResume()
        mWifiStateMonitor.register(context)
        mBluetoothStateMonitor.register(context)
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
        mWifiStateMonitor.unregister(context)
        mBluetoothStateMonitor.unregister(context)
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
            Log.d(TAG, "handleItemClick: $this")
            if(status != 0 && status != R.string.status_rejected) {
                return
            }
        }
        mShareViewModel.peerState.status = 0
        if (peer.id == mViewModel.mPeerPicked) {
            mViewModel.mPeerPicked = null
            mBinding.btnSend.isEnabled = false
        } else {
            mViewModel.mPeerPicked = peer.id
            mBinding.btnSend.isEnabled = true
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
