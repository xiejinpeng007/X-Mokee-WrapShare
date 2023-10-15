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
package org.mokee.warpshare.presentation.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mokee.warpshare.R
import org.mokee.warpshare.core.airdrop.AirDropManager
import org.mokee.warpshare.domain.data.Entity
import org.mokee.warpshare.domain.data.Peer
import org.mokee.warpshare.databinding.ActivityMainBinding
import org.mokee.warpshare.presentation.PermissionUtil
import org.mokee.warpshare.presentation.view.PeersAdapter
import org.mokee.warpshare.presentation.receiver.BluetoothStateMonitor
import org.mokee.warpshare.presentation.receiver.TriggerReceiver
import org.mokee.warpshare.presentation.receiver.WifiStateMonitor
import org.mokee.warpshare.presentation.settings.SettingsActivity
import org.mokee.warpshare.presentation.setup.SetupFragment

class MainActivity : AppCompatActivity() {
    private val mPeersAdapter = PeersAdapter(this::onItemClick, this::onItemCancelClick)
    private val mViewModel by viewModels<MainViewModel>()
    private lateinit var mBinding: ActivityMainBinding

    private var mPickFileLauncher: ActivityResultLauncher<String>? = null

    private val mWifiStateMonitor = object : WifiStateMonitor() {
        override fun onReceive(context: Context, intent: Intent) {
            setupOrStartDiscover()
        }
    }
    private val mBluetoothStateMonitor = object : BluetoothStateMonitor() {
        override fun onReceive(context: Context, intent: Intent) {
            setupOrStartDiscover()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(mBinding.root)

        mPickFileLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) {
            this.onGetMultipleContent(it)
        }

        mViewModel.appModule.mAirDropManager.registerTrigger(TriggerReceiver.getTriggerIntent(this))
        mBinding.recyclerViewPeers.adapter = mPeersAdapter
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        mViewModel.peerListLiveData.observe(this) {
            mPeersAdapter.submitList(it)
        }
        lifecycleScope.launch {
            mViewModel.peerUpdateFlow.flowWithLifecycle(lifecycle).collect {
                updatePeerInfo(it)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        mWifiStateMonitor.register(this)
        mBluetoothStateMonitor.register(this)
        setupOrStartDiscover()
    }

    override fun onPause() {
        super.onPause()
        if (mViewModel.mIsDiscovering && !mViewModel.mShouldKeepDiscovering) {
            mViewModel.appModule.stopDiscover()
            mViewModel.mIsDiscovering = false
        }
        mWifiStateMonitor.unregister(this)
        mBluetoothStateMonitor.unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        mPickFileLauncher?.unregister()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupOrStartDiscover() {
        supportFragmentManager.setFragmentResult(SetupFragment.tagForStateChange, Bundle())
        if (mViewModel.mIsInSetup) return

        val ready = PermissionUtil.checkAirDropIsReady() == AirDropManager.STATUS_OK
        Log.d(TAG, "setupIfNeeded: $ready")
        if (!ready) {
            mViewModel.mIsInSetup = true
            SetupFragment.show(supportFragmentManager)
        } else {
            if (!mViewModel.mIsDiscovering) {
                mViewModel.appModule.startDiscover(mViewModel)
                mViewModel.mIsDiscovering = true
            }
        }
    }

    private fun onItemClick(peer: Peer) {
        mViewModel.mPeerPicked = peer
        mViewModel.mShouldKeepDiscovering = true
        mPickFileLauncher?.launch("*/*")
    }

    private fun onItemCancelClick(peer: Peer) {
        peer.status.sending?.cancel()
        mViewModel.handleSendFailed(peer)
    }

    /**
     * 选择文件后的回调
     */
    private fun onGetMultipleContent(uriList:List<Uri>){
        Log.d(TAG, "onGetMultipleContent: ${uriList.joinToString()}")
        if (uriList.isEmpty()) {
            return
        }
        mViewModel.mShouldKeepDiscovering = false
        val peer = mViewModel.ensurePickedPeer() ?: return
        val applicationContext = application ?: return
        mViewModel.sendFile(peer, uriList.map{ Entity(applicationContext, it, "") })
    }

    private fun updatePeerInfo(peer: Peer?){
        if(peer == null) {
            return
        }
        val desireIndex = mPeersAdapter.currentList.indexOfFirst { it.id == peer.id }
        if(desireIndex != -1) {
            mPeersAdapter.notifyItemChanged(desireIndex, "updatePeerInfo")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
