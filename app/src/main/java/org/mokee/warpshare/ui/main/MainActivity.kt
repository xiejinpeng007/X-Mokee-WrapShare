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
package org.mokee.warpshare.ui.main

import android.Manifest.permission
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import org.mokee.warpshare.BluetoothStateMonitor
import org.mokee.warpshare.MainPeerState
import org.mokee.warpshare.PartialWakeLock
import org.mokee.warpshare.PeersAdapter
import org.mokee.warpshare.R
import org.mokee.warpshare.SettingsActivity
import org.mokee.warpshare.SetupActivity
import org.mokee.warpshare.TriggerReceiver
import org.mokee.warpshare.WifiStateMonitor
import org.mokee.warpshare.airdrop.AirDropManager.STATUS_OK
import org.mokee.warpshare.base.DiscoverListener
import org.mokee.warpshare.base.Peer
import org.mokee.warpshare.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private val mPeersAdapter = PeersAdapter(this::onItemClick, this::onItemCancelClick)
    private var mWakeLock: PartialWakeLock? = null
    private var mIsInSetup = false
    private lateinit var mViewModel: MainViewModel
    private var mIsDiscovering = false
    private var mBinding: ActivityMainBinding? = null

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
        mBinding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(mBinding!!.getRoot())
        mViewModel =
            ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(this.application))[MainViewModel::class.java]
        mWakeLock = PartialWakeLock(this, TAG)

        // TODO
//        mAirDropManager.registerTrigger(TriggerReceiver.getTriggerIntent(this));
        mBinding!!.peers.adapter = mPeersAdapter
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO
//        mAirDropManager.destroy();
//        mNearShareManager.destroy();
    }

    override fun onResume() {
        super.onResume()
        mWifiStateMonitor.register(this)
        mBluetoothStateMonitor.register(this)
        if (setupIfNeeded()) {
            return
        }
        if (!mIsDiscovering) {
            // TODO
//            mAirDropManager.startDiscover(this);
//            mNearShareManager.startDiscover(this);
            mIsDiscovering = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (mIsDiscovering && !mViewModel.mShouldKeepDiscovering) {
            // TODO
//            mAirDropManager.stopDiscover();
//            mNearShareManager.stopDiscover();
            mIsDiscovering = false
        }
        mWifiStateMonitor.unregister(this)
        mBluetoothStateMonitor.unregister(this)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_PICK -> {
                mViewModel.mShouldKeepDiscovering = false
                val prePickedPeerId = mViewModel.mPeerPicked
                if (resultCode == RESULT_OK && prePickedPeerId != null && data != null) {
                    val peer = mViewModel.findPeerById(prePickedPeerId) ?: return
                    val clipData = data.clipData
                    val type = data.type ?: return
                    if (clipData == null) {
                        val uri = data.data ?: return
                        mViewModel.sendFile(peer, uri, type)
                    } else {
                        mViewModel.sendFile(peer, data.clipData, type)
                    }
                }
            }

            REQUEST_SETUP -> {
                mIsInSetup = false
                if (resultCode != RESULT_OK) {
                    finish()
                } else {
                    mViewModel.appModule.mAirDropManager.registerTrigger(
                        TriggerReceiver.getTriggerIntent(
                            this
                        )
                    );
                }
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun setupIfNeeded(): Boolean {
        if (mIsInSetup) {
            return true
        }
        val granted =
            checkSelfPermission(permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val ready = mViewModel.appModule.mAirDropManager.ready() == STATUS_OK
        return if (!granted || !ready) {
            mIsInSetup = true
            startActivityForResult(
                Intent(this, SetupActivity::class.java),
                REQUEST_SETUP
            )
            true
        } else {
            false
        }
    }

    private fun onItemClick(peer: Peer) {
        mViewModel.mPeerPicked = peer.id
        mViewModel.mShouldKeepDiscovering = true
        val requestIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setType("*/*")
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(
            Intent.createChooser(requestIntent, "File"),
            REQUEST_PICK
        )
    }

    private fun onItemCancelClick(peer: Peer) {
        peer.status.sending?.cancel()
        mViewModel.handleSendFailed(peer)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_PICK = 1
        private const val REQUEST_SETUP = 2
    }
}
