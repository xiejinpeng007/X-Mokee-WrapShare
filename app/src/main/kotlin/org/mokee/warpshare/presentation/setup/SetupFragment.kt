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
package org.mokee.warpshare.presentation.setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import org.mokee.warpshare.core.airdrop.AirDropManager
import org.mokee.warpshare.databinding.ActivitySetupBinding
import org.mokee.warpshare.presentation.PermissionUtil
import org.mokee.warpshare.presentation.main.MainViewModel

class SetupFragment : DialogFragment() {
    private var _mBinding: ActivitySetupBinding? = null
    private val mBinding: ActivitySetupBinding
        get() = _mBinding!!
    private val mViewModel by activityViewModels<MainViewModel>()

    private lateinit var permissionUtil :PermissionUtil

    override fun onAttach(context: Context) {
        super.onAttach(context)
        permissionUtil = PermissionUtil(requireActivity())
        lifecycle.addObserver(permissionUtil)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _mBinding = ActivitySetupBinding.inflate(inflater)
        return mBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        mBinding.wifi.setOnClickListener { setupWifi() }
        mBinding.bt.setOnClickListener { turnOnBluetooth() }

        parentFragmentManager.setFragmentResultListener(tagForStateChange, viewLifecycleOwner) { _, _ ->
            updateState()
        }
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    private fun updateState() {
        when (PermissionUtil.checkAirDropIsReady()) {
            AirDropManager.STATUS_NO_WIFI -> {
                mBinding.groupPerm.visibility = View.GONE
                mBinding.groupWifi.visibility = View.VISIBLE
                mBinding.groupBt.visibility = View.GONE
            }
            AirDropManager.STATUS_NO_BLUETOOTH -> {
                mBinding.groupPerm.visibility = View.GONE
                mBinding.groupWifi.visibility = View.GONE
                mBinding.groupBt.visibility = View.VISIBLE
            }
            else -> {
                try {
                    mViewModel.onSuccessConfigAirDrop(requireContext())
                    dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dismiss setup fragment :${e.message}")
                }
            }
        }
    }

    private fun setupWifi() {
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    private fun turnOnBluetooth() {
        if(PermissionUtil.checkAirDropIsReady() == AirDropManager.STATUS_OK){
            updateState()
        }else {
            permissionUtil.requestBLEPermission()
        }
    }

    companion object {
        const val TAG = "SetupFragment"
        const val tagForStateChange = "statedChangeForSetup"

        fun show(fragmentManager: FragmentManager){
            Log.d(TAG, "SetupFragment show")
            val fragment = SetupFragment()
            fragment.show(fragmentManager, TAG)
        }
    }
}
