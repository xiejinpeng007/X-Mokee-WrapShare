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
package org.mokee.warpshare

import android.Manifest.permission
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.ArrayMap
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.mokee.warpshare.airdrop.AirDropManager
import org.mokee.warpshare.airdrop.AirDropPeer
import org.mokee.warpshare.base.DiscoverListener
import org.mokee.warpshare.base.Entity
import org.mokee.warpshare.base.Peer
import org.mokee.warpshare.base.SendListener
import org.mokee.warpshare.base.SendingSession
import org.mokee.warpshare.databinding.FragmentShareBinding
import org.mokee.warpshare.nearbysharing.NearShareManager
import org.mokee.warpshare.nearbysharing.NearSharePeer
import org.mokee.warpshare.ui.main.MainViewModel

class ShareBottomSheetFragment : BottomSheetDialogFragment(), DiscoverListener {
    private var _mBinding: FragmentShareBinding? = null
    val mBinding: FragmentShareBinding
        get() = _mBinding!!

    private val mPeers = ArrayMap<String?, Peer>()
    private val mEntities: MutableList<Entity> = ArrayList()
    private val mAdapter = PeersAdapter(this::handleItemClick)
    private var mPeerPicked: String? = null
    private var mPeerStatus = 0
    private var mBytesTotal: Long = -1
    private var mBytesSent: Long = 0
    private var mWakeLock: PartialWakeLock? = null
    private var mIsInSetup = false

    private var mIsDiscovering = false
    private var mShouldKeepDiscovering = false
    private var mSending: SendingSession? = null

    private lateinit var mViewModel: MainViewModel
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
    ): View? {
        return inflater.inflate(R.layout.fragment_share, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel =
            ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory(requireActivity().application)
            )[MainViewModel::class.java]
        mBinding.peers.adapter = mAdapter
        val clipData = activity?.intent?.clipData
        if (clipData == null) {
            Log.w(TAG, "ClipData should not be null")
            handleSendFailed()
            return
        }
        val type = activity?.intent?.type
        for (i in 0 until clipData.itemCount) {
            val entity = Entity(context, clipData.getItemAt(i).uri, type)
            if (entity.ok()) {
                mEntities.add(entity)
            }
        }
        val count = mEntities.size
        val titleText = resources.getQuantityString(R.plurals.send_files_to, count, count)
        val titleView = view.findViewById<TextView>(R.id.title)
        titleView.text = titleText
        mBinding.send.setOnClickListener {
            sendFile(mPeers[mPeerPicked], mEntities)
        }
        if (mEntities.isEmpty()) {
            Log.w(TAG, "No file was selected")
            Toast.makeText(context, R.string.toast_no_file, Toast.LENGTH_SHORT).show()
            handleSendFailed()
            activity?.finish()
        }
    }

    override fun onResume() {
        super.onResume()
        mWifiStateMonitor.register(context)
        mBluetoothStateMonitor.register(context)
        if (setupIfNeeded()) {
            return
        }
        if (!mIsDiscovering) {
            // TODO start
            mIsDiscovering = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (mIsDiscovering && !mShouldKeepDiscovering) {
            // TODO stop
            mIsDiscovering = false
        }
        mWifiStateMonitor.unregister(context)
        mBluetoothStateMonitor.unregister(context)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mSending?.cancel()
        mSending = null
        activity?.finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_SETUP -> {
                mIsInSetup = false
                if (resultCode != Activity.RESULT_OK) {
                    activity?.finish()
                }
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onPeerFound(peer: Peer) {
        Log.d(TAG, "Found: " + peer.id + " (" + peer.name + ")")
        mPeers[peer.id] = peer
        mAdapter.notifyDataSetChanged()
    }

    override fun onPeerDisappeared(peer: Peer) {
        Log.d(TAG, "Disappeared: " + peer.id + " (" + peer.name + ")")
        if (peer.id == mPeerPicked) {
            mPeerPicked = null
        }
        mPeers.remove(peer.id)
        mAdapter.notifyDataSetChanged()
    }

    private fun setupIfNeeded(): Boolean {
        if (mIsInSetup) {
            return true
        }
        val granted =
            activity?.checkSelfPermission(permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val ready = mViewModel.appModule.mAirDropManager.ready() == AirDropManager.STATUS_OK
        return if (!granted || !ready) {
            mIsInSetup = true
            startActivityForResult(
                Intent(context, SetupActivity::class.java),
                REQUEST_SETUP
            )
            true
        } else {
            false
        }
    }

    private fun handleItemClick(peer: Peer) {
        if (mPeerStatus != 0 && mPeerStatus != R.string.status_rejected) {
            return
        }
        mPeerStatus = 0
        if (peer.id == mPeerPicked) {
            mPeerPicked = null
            mBinding.send.setEnabled(false)
        } else {
            mPeerPicked = peer.id
            mBinding.send.setEnabled(true)
        }
        mAdapter.notifyDataSetChanged()
    }

    private fun handleSendConfirming() {
        mPeerStatus = R.string.status_waiting_for_confirm
        mBytesTotal = -1
        mBytesSent = 0
        mAdapter.notifyDataSetChanged()
        mBinding.send.setEnabled(false)
        mBinding.discovering.visibility = View.GONE
        mShouldKeepDiscovering = true
        mWakeLock?.acquire()
    }

    private fun handleSendRejected() {
        mSending = null
        mPeerStatus = R.string.status_rejected
        mAdapter.notifyDataSetChanged()
        mBinding.send.setEnabled(true)
        mBinding.discovering.visibility = View.VISIBLE
        mShouldKeepDiscovering = false
        mWakeLock?.release()
        Toast.makeText(context, R.string.toast_rejected, Toast.LENGTH_SHORT).show()
    }

    private fun handleSending() {
        mPeerStatus = R.string.status_sending
        mAdapter.notifyDataSetChanged()
    }

    private fun handleSendSucceed() {
        mSending = null
        mShouldKeepDiscovering = false
        mWakeLock?.release()
        Toast.makeText(context, R.string.toast_completed, Toast.LENGTH_SHORT).show()
        activity?.setResult(Activity.RESULT_OK)
        dismiss()
    }

    private fun handleSendFailed() {
        mSending = null
        mPeerPicked = null
        mPeerStatus = 0
        mAdapter.notifyDataSetChanged()
        mBinding.send.setEnabled(true)
        mBinding.discovering.visibility = View.VISIBLE
        mShouldKeepDiscovering = false
        mWakeLock?.release()
    }

    private fun <P : Peer?> sendFile(peer: P, entities: List<Entity>) {
        handleSendConfirming()
        val listener: SendListener = object : SendListener {
            override fun onAccepted() {
                handleSending()
            }

            override fun onRejected() {
                handleSendRejected()
            }

            override fun onProgress(bytesSent: Long, bytesTotal: Long) {
                mBytesSent = bytesSent
                mBytesTotal = bytesTotal
                mAdapter.notifyDataSetChanged()
            }

            override fun onSent() {
                handleSendSucceed()
            }

            override fun onSendFailed() {
                handleSendFailed()
            }
        }
        if (peer is AirDropPeer) {
            mSending =
                mViewModel.appModule.mAirDropManager.send(peer as AirDropPeer, entities, listener)
        } else if (peer is NearSharePeer) {
            mSending = mViewModel.appModule.mNearShareManager.send(
                (peer as NearSharePeer),
                entities,
                listener
            )
        }
    }

    companion object {
        private const val TAG = "ShareBottomSheetFragment"
        private const val REQUEST_SETUP = 1
    }
}
